#!/usr/bin/env python3
"""Read-only DEX class/method index. No proprietary code is extracted."""
import argparse, struct, zipfile

def u32(data, pos): return struct.unpack_from('<I',data,pos)[0]
def uleb(data, pos):
    value,shift=0,0
    while True:
        b=data[pos];pos+=1
        value|=(b&127)<<shift
        if not b&128: return value,pos
        shift+=7

def parse_dex(b, q):
    if b[:3]!=b'dex': raise ValueError('Not a dex file')
    n,off=u32(b,0x38),u32(b,0x3c)
    strings=[]
    for i in range(n):
        pos=u32(b,off+4*i)
        _,pos=uleb(b,pos)
        end=b.index(0,pos)
        strings.append(b[pos:end].decode('utf-8','replace'))
    n,off=u32(b,0x40),u32(b,0x44)
    typ=[strings[u32(b,off+4*i)] for i in range(n)]
    n,off=u32(b,0x58),u32(b,0x5c)
    hits={}
    for i in range(n):
        cls,proto,name=struct.unpack_from('<HHI',b,off+i*8)
        clazz=typ[cls]
        if q.lower() in clazz.lower():
            hits.setdefault(clazz,[]).append(strings[name])
    return hits

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('apk');p.add_argument('--filter',default='TabsManager');args=p.parse_args()
    with zipfile.ZipFile(args.apk) as z:
        for name in z.namelist():
            if name.startswith('classes') and name.endswith('.dex'):
                matches=parse_dex(z.read(name),args.filter)
                for cls,meth in sorted(matches.items()):
                    print(name,cls,'methods=',len(meth))
                    print('  ', ', '.join(sorted(set(meth))[:130]))
if __name__=='__main__':main()
