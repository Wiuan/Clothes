import fs from 'fs'
import path from 'path'
import os from 'os'

const base = path.join(os.tmpdir(), 'xlsx_inspect', 'unzipped')

function readSharedStrings() {
  const p = path.join(base, 'xl/sharedStrings.xml')
  if (!fs.existsSync(p)) return []
  const s = fs.readFileSync(p, 'utf8')
  return [...s.matchAll(/<t[^>]*>([^<]*)<\/t>/g)].map((m) => m[1])
}

const ss = readSharedStrings()
console.log('shared strings count', ss.length)
console.log('samples:', ss.filter((t) => t.length < 40).slice(0, 60))

for (const name of ['sheet1.xml', 'sheet2.xml']) {
  const p = path.join(base, 'xl/worksheets', name)
  if (!fs.existsSync(p)) continue
  const s = fs.readFileSync(p, 'utf8')
  const anchors = (s.match(/xdr:twoCellAnchor/g) || []).length
  console.log(name, 'size kb', Math.round(s.length / 1024), 'inline anchors', anchors)
}

const d1 = path.join(base, 'xl/drawings/drawing1.xml')
if (fs.existsSync(d1)) {
  const anchors = (fs.readFileSync(d1, 'utf8').match(/xdr:twoCellAnchor/g) || []).length
  console.log('drawing1 anchors', anchors)
}
const d2 = path.join(base, 'xl/drawings/drawing2.xml')
if (fs.existsSync(d2)) {
  const anchors = (fs.readFileSync(d2, 'utf8').match(/xdr:twoCellAnchor/g) || []).length
  console.log('drawing2 anchors', anchors)
}

const media = fs.readdirSync(path.join(base, 'xl/media'))
console.log('media files', media.length)
