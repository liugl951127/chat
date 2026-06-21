/**
 * 设备指纹 (前端生成, 存 localStorage)
 */
export function getDeviceId(): string {
  let id = localStorage.getItem('fin_device_id')
  if (!id) {
    id = 'dev-' + Math.random().toString(36).substring(2, 10)
      + '-' + Date.now().toString(36)
    localStorage.setItem('fin_device_id', id)
  }
  return id
}
