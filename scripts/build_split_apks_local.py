#!/usr/bin/env python3
"""Build complete, locally signed split APKs for personal testing.
No commercial APK is uploaded. Never bypass licensing checks.
Requires Python 3, Java, the Android SDK apksigner.jar, and cryptography for key generation.
"""
import argparse
import struct
import subprocess
import tempfile
import zipfile
from pathlib import Path
from patch_apk_local import pool_read, write_utf16_len, zip_repack

APP='acr.browser.lightning.app.BrowserApp'
INJECT='org.local.tabgroups.InjectedBrowserApp'
PACKAGE='idm.internet.download.manager.plus'

def patch_manifest(data, clone=None, inject=False):
    replacements=[]
    if inject: replacements.append((APP, INJECT))
    if clone: replacements.extend(((PACKAGE, clone), (PACKAGE+'.', clone+'.')))
    if not replacements: return data
    strings, pool_size, header_size=pool_read(data)
    vals=[]
    hits=0
    for item in strings:
        v=item
        for original, replacement in replacements:
            if item==original:
                v=replacement;break
            if original.endswith('.') and item.startswith(original):
                v=replacement+item[len(original):];break
        vals.append(v)
        if v!=item: hits+=1
    if hits==0: raise ValueError('Manifest patch mismatch')
    offsets=[]; payload=b''
    for value in vals:
        offsets.append(len(payload))
        payload+=write_utf16_len(len(value))+value.encode('utf-16le')+b'\x00\x00'
    payload+=b'\x00'*((-len(payload))%4)
    header=bytearray(data[8:8+header_size])
    struct.pack_into('<I',header,4,header_size+len(vals)*4+len(payload))
    struct.pack_into('<I',header,20,header_size+len(vals)*4)
    new_pool=bytes(header)+struct.pack('<%sI'%len(vals),*offsets)+payload
    result=bytearray(data[:8]+new_pool+data[8+pool_size:])
    struct.pack_into('<I',result,4,len(result))
    return bytes(result)

def repack_split(original, output, manifest):
    with zipfile.ZipFile(original) as inp,zipfile.ZipFile(output,'w') as dst:
        for item in inp.infolist():
            if item.filename.startswith('META-INF/') and item.filename.upper().endswith(('.RSA','.DSA','.EC','.SF')): continue
            value=manifest if item.filename=='AndroidManifest.xml' else inp.read(item.filename)
            info=zipfile.ZipInfo(item.filename,item.date_time)
            info.compress_type=item.compress_type
            info.external_attr=item.external_attr
            if info.compress_type==zipfile.ZIP_STORED and info.filename.endswith(('.so','.dex','.arsc')):
                start=dst.fp.tell()+30+len(info.filename.encode('utf8'))
                pad=(-start-4)%4
                info.extra=struct.pack('<HH',0,pad)+b'\x00'*pad
            dst.writestr(info,value)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for arg in ('apks','dex','key','cert','apksigner-jar','out'):
        p.add_argument('--'+arg,required=True,type=Path)
    p.add_argument('--clone-package',default=None)
    a=p.parse_args()
    if not a.dex.read_bytes().startswith(b'dex\n'): raise ValueError('Invalid DEX')
    if a.clone_package==PACKAGE: raise ValueError('Clone ID must differ from original')
    with tempfile.TemporaryDirectory() as work:
        temp=Path(work)
        with zipfile.ZipFile(a.apks) as src:
            names=[n for n in src.namelist() if n.endswith('.apk') and '/' not in n]
            if names.count('base.apk')!=1 or len(names)<2:
                raise ValueError('Complete .apks with base and splits required')
            for name in names:(temp/name).write_bytes(src.read(name))
        signed=[]
        for name in names:
            orig=temp/name
            with zipfile.ZipFile(orig) as zin:
                manifest=patch_manifest(zin.read('AndroidManifest.xml'),
                     a.clone_package,inject=name=='base.apk')
            unsigned=temp/('unsigned_'+name)
            done=temp/('signed_'+name)
            if name=='base.apk':
                zip_repack(orig,unsigned,manifest,a.dex.read_bytes())
            else:repack_split(orig,unsigned,manifest)
            cmd=['java','-jar',str(a.apksigner_jar.resolve())]
            subprocess.run(cmd+['sign','--key',str(a.key.resolve()),'--cert',
               str(a.cert.resolve()),'--out',str(done),str(unsigned)],check=True)
            proc=subprocess.run(cmd+['verify','--verbose',str(done)],
                check=True,text=True,capture_output=True)
            if 'Verifies' not in proc.stdout: raise ValueError('Signature verification failed')
            with zipfile.ZipFile(done) as z:
                if z.testzip():raise ValueError('APK CRC failed')
            signed.append((name,done))
        a.out.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(a.out,'w',zipfile.ZIP_DEFLATED,compresslevel=7) as dest:
            for name,done in signed:dest.write(done,arcname=name)
        with zipfile.ZipFile(a.out) as z:
            if z.testzip():raise ValueError('APKS CRC failed')
    print('BUILT',a.out,'splits=',len(signed))
    print('Not tested on Android. Clones may fail license verification; original package cannot be updated with a test key.')

if __name__=='__main__':main()
