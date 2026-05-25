# -*- coding: utf-8 -*-
import zipfile
import os
import re
import xml.etree.ElementTree as ET

paths = [
    r'c:\Users\admin\Desktop\衣服.xlsx',
    r'd:\software\IntelliJ IDEA 2022.1.4\IdeaProject\Clothes\衣服.xlsx',
]

path = next((p for p in paths if os.path.exists(p)), None)
if not path:
    print('FILE_NOT_FOUND')
    raise SystemExit(1)

print('PATH:', path)
z = zipfile.ZipFile(path)

root = ET.fromstring(z.read('xl/workbook.xml'))
ns = {'m': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main', 'r': 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'}
for s in root.findall('.//m:sheet', ns):
    print('SHEET:', s.attrib.get('name'), s.attrib.get('{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id'))

media = [n for n in z.namelist() if n.startswith('xl/media/')]
print('MEDIA_COUNT:', len(media))
for n in media[:20]:
    print(' ', n)

if 'xl/sharedStrings.xml' in z.namelist():
    ss = z.read('xl/sharedStrings.xml').decode('utf-8', errors='ignore')
    print('SHARED_STRINGS_SI:', ss.count('<si'))

# list sheet xml files
for n in sorted(z.namelist()):
    if n.startswith('xl/worksheets/sheet') and n.endswith('.xml'):
        print('WORKSHEET_XML:', n)
