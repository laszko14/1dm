#!/usr/bin/env python3
"""Locally inject an independently compiled DEX into the owner's own APK.

Do not publish source APK or patched APK in a public repository.
This does not bypass license checks or restore omitted split packages.
Requires cryptography; signing is experimental and warrants apksigner verification.
"""
import argparse
import io
import os
import hashlib
import shutil
import struct
import zipfile
from datetime import datetime, timedelta, timezone
from pathlib import Path
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.x509.oid import NameOID

HEADER = struct.Struct('<HHI')
ORIGINAL_APP = 'acr.browser.lightning.app.BrowserApp'
INJECTED_APP = 'org.local.tabgroups.InjectedBrowserApp'
SIG_BLOCK_MAGIC = b'APK Sig Block 42'
V2_BLOCK_ID = 0x7109871a
SIG_ALG_RSA_SHA256 = 0x0103


def u32(data, pos): return struct.unpack_from('<I',data,pos)[0]
def u16(data, pos): return struct.unpack_from('<H',data,pos)[0]

def read_utf16_len(src, p):
    value = u16(src,p); p+=2
    if value & 0x8000:
        value = ((value & 0x7fff)<<16) | u16(src,p);p+=2
    return value,p

def write_utf16_len(n):
    if n < 0x8000: return struct.pack('<H',n)
    return struct.pack('<HH', (n>>16)|0x8000,n&0xffff)

def pool_read(src):
    p=8
    type_id,head,size,num,styles,flags,strings_start,style_start=struct.unpack_from('<HHIIIIII',src,p)
    if type_id != 1 or flags & 256 or styles:
        raise ValueError('Unexpected AXML string pool structure')
    offsets=[u32(src,p+head+4*i) for i in range(num)]
    strings=[]
    for off in offsets:
        q=p+strings_start+off
        n,q=read_utf16_len(src,q)
        strings.append(src[q:q+n*2].decode('utf-16le','strict'))
    return strings,size,head

def manifest_patch(src, no_split_requirement=False):
    if src[:2] != b'\x03\x00': raise ValueError('Not Android AXML')
    strings,pool_size,pool_head=pool_read(src)
    if strings.count(ORIGINAL_APP) != 1: raise ValueError('Original class name missing/ambiguous in manifest')
    idx=strings.index(ORIGINAL_APP)
    # Ensure old string index is only used as the Application android:name.
    endpool=8+pool_size
    matches=[]
    pos=endpool
    while pos < len(src):
        typ,head,size=HEADER.unpack_from(src,pos)
        if not size: raise ValueError('Bad XML chunk')
        if typ==0x0102 and strings[u32(src,pos+20)]=='application':
            n=u16(src,pos+28); astart=u16(src,pos+24); asize=u16(src,pos+26)
            for i in range(n):
                a=pos+16+astart+asize*i
                if strings[u32(src,a+4)]=='name':
                    matches.append((u32(src,a+8),u32(src,a+16)))
        pos+=size
    if not matches or not any(idx in a for a in matches): raise ValueError('Cannot find application android:name')
    # No original text is copied out; replace only this single name in stringpool.
    strings[idx]=INJECTED_APP
    offsets=[]; payload=b''
    for s in strings:
        offsets.append(len(payload))
        payload+=write_utf16_len(len(s))+s.encode('utf-16le')+b'\x00\x00'
    payload+=b'\x00'*((-len(payload))%4)
    header_byte=bytearray(src[8:8+pool_head])
    new_pool_size=pool_head + 4*len(strings)+len(payload)
    struct.pack_into('<I',header_byte,4,new_pool_size)
    struct.pack_into('<I',header_byte,20,pool_head+4*len(strings))
    new_pool=bytes(header_byte)+struct.pack('<%sI'%len(offsets),*offsets)+payload
    rest=bytearray(src[endpool:])
    if no_split_requirement:
        pos=0
        while pos<len(rest):
            typ,head,size=HEADER.unpack_from(rest,pos)
            if not size: raise ValueError('Unexpected zero XML chunk')
            if typ==0x0102 and strings[u32(rest,pos+20)]=='manifest':
                n=u16(rest,pos+28); astart=u16(rest,pos+24); asize=u16(rest,pos+26)
                remove=[]
                for i in range(n):
                    a=pos+16+astart+asize*i
                    name=strings[u32(rest,a+4)]
                    if name in ('requiredSplitTypes','splitTypes'):
                        remove.append(a)
                for a in reversed(remove):
                    del rest[a:a+asize]
                if remove:
                    struct.pack_into('<H',rest,pos+28,n-len(remove))
                    struct.pack_into('<I',rest,pos+4,size-len(remove)*asize)
                break
            pos+=size
    output=bytearray(src[:8]+new_pool+rest)
    struct.pack_into('<I',output,4,len(output))
    return bytes(output)


def zip_repack(original, out, manifest, dex):
    with zipfile.ZipFile(original) as z:
        dex_names=[n for n in z.namelist() if n.startswith('classes') and n.endswith('.dex')]
        next_dex='classes%d.dex'%(len(dex_names)+1)
        if next_dex in z.namelist(): raise ValueError('DEX name already in ZIP')
        with zipfile.ZipFile(out,'w',allowZip64=True) as dst:
            for entry in z.infolist():
                # Android signing scheme v2 will replace the original signature.
                if entry.filename.startswith('META-INF/') and entry.filename.upper().endswith(('.RSA','.DSA','.EC','.SF')):
                    continue
                content=manifest if entry.filename=='AndroidManifest.xml' else z.read(entry.filename)
                info=zipfile.ZipInfo(entry.filename,entry.date_time)
                info.compress_type=entry.compress_type
                info.external_attr=entry.external_attr
                info.create_system=entry.create_system
                info.flag_bits=entry.flag_bits & 0x800
                info.extra=b''
                if entry.compress_type==zipfile.ZIP_STORED and (entry.filename=='resources.arsc' or entry.filename.endswith('.dex')):
                    # Android requires uncompressed resources.arsc to be aligned.
                    base=dst.fp.tell()+30+len(info.filename.encode('utf-8'))
                    pad=(-base-4)%4
                    info.extra=struct.pack('<HH',0,pad)+b'\x00'*pad
                dst.writestr(info,content,compress_type=entry.compress_type,compresslevel=6)
            info=zipfile.ZipInfo(next_dex)
            info.compress_type=zipfile.ZIP_STORED
            base=dst.fp.tell()+30+len(next_dex.encode('utf-8'))
            pad=(-base-4)%4
            info.extra=struct.pack('<HH',0,pad)+b'\x00'*pad
            dst.writestr(info,dex,compress_type=zipfile.ZIP_STORED)
    return next_dex


def bytes_prefixed(b):return struct.pack('<I',len(b))+b

def digest_apk_content(body, central_directory, eocd):
    chunks=[]
    for data in (body,central_directory,eocd):
        for index in range(0,len(data),1<<20):
            chunk=data[index:index+(1<<20)]
            chunks.append(hashlib.sha256(b'\xa5'+struct.pack('<I',len(chunk))+chunk).digest())
    return hashlib.sha256(b'\x5a'+struct.pack('<I',len(chunks))+b''.join(chunks)).digest()


def sign_v2(apk_path, out_path, key_file, cert_file):
    raw=Path(apk_path).read_bytes()
    # ZIP EOCD only resides in last 65557 bytes.
    marker=raw.rfind(b'PK\x05\x06',max(0,len(raw)-65557))
    if marker<0: raise ValueError('Missing ZIP EOCD')
    cd_start=u32(raw,marker+16)
    if cd_start>=marker or raw[cd_start:cd_start+4]!=b'PK\x01\x02': raise ValueError('Bad central dir offset')
    eocd=bytearray(raw[marker:]);struct.pack_into('<I',eocd,16,cd_start)
    dig=digest_apk_content(raw[:cd_start],raw[cd_start:marker],bytes(eocd))
    key=rsa.generate_private_key(public_exponent=65537,key_size=3072)
    subject=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'Personal 1DM+ Groups Test')])
    now=datetime.now(timezone.utc)
    cert=x509.CertificateBuilder().subject_name(subject).issuer_name(subject).public_key(key.public_key())\
        .serial_number(x509.random_serial_number()).not_valid_before(now-timedelta(days=1))\
        .not_valid_after(now+timedelta(days=3650)).sign(key,hashes.SHA256())
    cert_der=cert.public_bytes(serialization.Encoding.DER)
    digest_record=struct.pack('<I',SIG_ALG_RSA_SHA256)+bytes_prefixed(dig)
    signed_data=bytes_prefixed(bytes_prefixed(digest_record))+bytes_prefixed(bytes_prefixed(cert_der))+bytes_prefixed(b'')
    signature=key.sign(signed_data,padding.PKCS1v15(),hashes.SHA256())
    sig_record=struct.pack('<I',SIG_ALG_RSA_SHA256)+bytes_prefixed(signature)
    pk_der=key.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
    signer=bytes_prefixed(signed_data)+bytes_prefixed(bytes_prefixed(sig_record))+bytes_prefixed(pk_der)
    signers=bytes_prefixed(signer)
    v2_value=bytes_prefixed(signers)
    entry=struct.pack('<Q',4+len(v2_value))+struct.pack('<I',V2_BLOCK_ID)+v2_value
    block_size=len(entry)+8+16
    signing_block=struct.pack('<Q',block_size)+entry+struct.pack('<Q',block_size)+SIG_BLOCK_MAGIC
    new_eocd=bytearray(raw[marker:]);struct.pack_into('<I',new_eocd,16,cd_start+len(signing_block))
    Path(out_path).write_bytes(raw[:cd_start]+signing_block+raw[cd_start:marker]+new_eocd)
    Path(key_file).write_bytes(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
    Path(cert_file).write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    return cert.fingerprint(hashes.SHA256()).hex(':')


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--apk',required=True)
    p.add_argument('--dex',required=True)
    p.add_argument('--out',required=True)
    p.add_argument('--remove-required-splits',action='store_true')
    opt=p.parse_args()
    out=Path(opt.out);out.parent.mkdir(parents=True,exist_ok=True)
    dex=Path(opt.dex).read_bytes()
    if not dex.startswith(b'dex\n'): raise ValueError('Not a compiled DEX file')
    with zipfile.ZipFile(opt.apk) as original:
        manifest=manifest_patch(original.read('AndroidManifest.xml'),opt.remove_required_splits)
    tmp=out.with_suffix('.unsigned.apk')
    next_dex=zip_repack(opt.apk,tmp,manifest,dex)
    fp=sign_v2(tmp,out,out.with_suffix('.private-key.pem'),out.with_suffix('.cer.pem'))
    tmp.unlink()
    with zipfile.ZipFile(out) as zz:
        if zz.testzip(): raise ValueError('APK zip corrupted')
    print('APK BUILT:',out,'size',out.stat().st_size,'injected',next_dex)
    print('SELF-SIGNED CERT SHA256:',fp)
    print('NOTE: must still verify on Android device; license / missing splits may block install.')

if __name__=='__main__': main()
