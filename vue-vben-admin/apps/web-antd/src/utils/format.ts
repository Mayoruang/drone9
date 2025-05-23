/**
 * 格式化日期
 * @param date 日期对象或时间戳
 * @param format 格式字符串
 * @returns 格式化后的日期字符串
 */
export function formatDate(date: Date | number, format: string = 'YYYY-MM-DD'): string {
  if (!date) return '';

  const d = typeof date === 'number' ? new Date(date) : date;

  // 如果是无效日期
  if (isNaN(d.getTime())) return '';

  const yyyy = d.getFullYear().toString();
  const mm = (d.getMonth() + 1).toString().padStart(2, '0');
  const dd = d.getDate().toString().padStart(2, '0');
  const hh = d.getHours().toString().padStart(2, '0');
  const ii = d.getMinutes().toString().padStart(2, '0');
  const ss = d.getSeconds().toString().padStart(2, '0');

  return format
    .replace(/YYYY/g, yyyy)
    .replace(/MM/g, mm)
    .replace(/DD/g, dd)
    .replace(/HH/g, hh)
    .replace(/mm/g, ii)
    .replace(/ss/g, ss);
}

/**
 * 格式化文件大小
 * @param bytes 字节数
 * @param decimals 小数位数
 * @returns 格式化后的文件大小字符串
 */
export function formatFileSize(bytes: number, decimals: number = 2): string {
  if (bytes === 0) return '0 Bytes';

  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];

  const i = Math.floor(Math.log(bytes) / Math.log(k));

  return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
}
