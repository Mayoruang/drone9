const fs = require('fs'); let content = fs.readFileSync('GeofenceList.vue', 'utf8'); content = content.replace(/\/\/ 生成缩略图SVG[\s\S]*?return generateFallbackSVG\(geofence\);[\s\S]*?\*\/[\s\S]*?};/, '// 生成缩略图
const generateThumbnail = (geofence: GeofenceData) => {
  return generateAspectRatioSVG(geofence, false);
};'); fs.writeFileSync('GeofenceList.vue', content);
