// 生成 App 图标：深色圆角底 + 琥珀色光圈（纯 node/zlib，无依赖）
const zlib = require('zlib');
const fs = require('fs');
const S = 192, CX = 96, CY = 96, R_BG = 42, RING_R = 60, RING_W = 9, BLADE_W = 4.4;

// 6 条光圈叶片线段（外圈点 -> 内部偏转点）
const blades = [];
const off = 140 * Math.PI / 180;
for (let k = 0; k < 6; k++) {
  const a1 = k * Math.PI / 3;
  const a2 = a1 + off;
  blades.push([CX + RING_R * Math.cos(a1), CY + RING_R * Math.sin(a1),
               CX + RING_R * Math.cos(a2), CY + RING_R * Math.sin(a2)]);
}
function distSeg(px, py, x1, y1, x2, y2) {
  const dx = x2 - x1, dy = y2 - y1;
  const t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)));
  const qx = x1 + t * dx, qy = y1 + t * dy;
  return Math.hypot(px - qx, py - qy);
}
// BGRA → 直接构造 RGBA
const raw = Buffer.alloc(S * (S * 4 + 1)); // 每行前置 filter byte 0
const sd = 0.9; // 抗锯齿过渡宽度
function aa(edge) { return Math.max(0, Math.min(1, (edge + sd / 2) / sd)); }
for (let y = 0; y < S; y++) {
  const rowStart = y * (S * 4 + 1);
  raw[rowStart] = 0;
  for (let x = 0; x < S; x++) {
    // 圆角矩形覆盖度
    const bx = Math.max(R_BG - x, x - (S - 1 - R_BG), 0);
    const by = Math.max(R_BG - y, y - (S - 1 - R_BG), 0);
    const bgAlpha = 1 - aa(Math.hypot(bx, by) - R_BG);
    let amber = 0;
    const dr = Math.abs(Math.hypot(x - CX, y - CY) - RING_R);
    amber = Math.max(amber, aa(dr - RING_W / 2) * (1 - aa(dr - RING_W / 2 - sd))); // 环带
    for (const b of blades) amber = Math.max(amber, 1 - aa(distSeg(x, y, b[0], b[1], b[2], b[3]) - BLADE_W));
    let r = 11, g = 11, b2 = 15, a = bgAlpha;          // 底色 #0B0B0F
    const ar = 240, ag = 160, ab = 60;                  // 琥珀 #F0A03C
    r = Math.round(r + (ar - r) * amber); g = Math.round(g + (ag - g) * amber); b2 = Math.round(b2 + (ab - b2) * amber);
    const i = rowStart + 1 + x * 4;
    raw[i] = r; raw[i + 1] = g; raw[i + 2] = b2; raw[i + 3] = Math.round(a * 255);
  }
}
// PNG 编码
function crc32(buf) {
  let table = crc32.table;
  if (!table) {
    table = crc32.table = [];
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
      table[n] = c >>> 0;
    }
  }
  let c = 0xFFFFFFFF;
  for (let i = 0; i < buf.length; i++) c = table[(c ^ buf[i]) & 0xFF] ^ (c >>> 8);
  return (c ^ 0xFFFFFFFF) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(S, 0); ihdr.writeUInt32BE(S, 4);
ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
const png = Buffer.concat([
  Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]),
  chunk('IHDR', ihdr),
  chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
  chunk('IEND', Buffer.alloc(0))
]);
fs.writeFileSync(process.argv[2], png);
console.log('icon written:', process.argv[2], png.length, 'bytes');
