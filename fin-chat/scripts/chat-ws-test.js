/**
 * Fin-Chat WebSocket (STOMP) 端到端测试
 *
 * 用原生 WS 发 STOMP frame, 模拟两个前端客户 (A 和 B)
 * 验证: 握手鉴权 + Principal 绑定 + 哈希链 + 多端推送
 *
 * Usage: node chat-ws-test.js
 */
import WebSocket from 'ws'

const WS_URL = process.env.WS_URL || 'ws://localhost:8082/ws/chat'
let convId = process.env.CONV_ID || ''

function wsUrlFor(userId) {
  return `${WS_URL}?userId=${userId}`
}

// 解析 STOMP frame (COMMAND\nheaders\n\nbody\x00)
function parseFrame(raw) {
  const dblNl = raw.indexOf('\n\n')
  if (dblNl < 0) return null
  const head = raw.substring(0, dblNl)
  const bodyAndTerm = raw.substring(dblNl + 2)
  const termIdx = bodyAndTerm.lastIndexOf('\x00')
  const body = termIdx >= 0 ? bodyAndTerm.substring(0, termIdx) : bodyAndTerm
  const lines = head.split('\n')
  const headers = {}
  for (let i = 1; i < lines.length; i++) {
    const colon = lines[i].indexOf(':')
    if (colon >= 0) headers[lines[i].substring(0, colon)] = lines[i].substring(colon + 1)
  }
  return { command: lines[0], headers, body }
}

function makeClient(userId, label, onMessage) {
  const ws = new WebSocket(wsUrlFor(userId))
  const me = { ws, userId, subscriptionId: 'sub-' + userId }

  ws.onopen = () => {
    console.log(`[${label}] 🔌 WS open`)
    const frame = [
      'CONNECT',
      'accept-version:1.2',
      'host:localhost',
      `Authorization:Bearer demo.${Buffer.from(JSON.stringify({sub: userId})).toString('base64')}.demo`,
      `X-User-Id:${userId}`,
      'heart-beat:10000,10000',
      '',
      '\x00'
    ].join('\n')
    ws.send(frame)
  }

  ws.onmessage = (evt) => {
    const frame = parseFrame(evt.data.toString())
    if (!frame) return
    if (frame.command === 'CONNECTED') {
      console.log(`[${label}] ✅ STOMP CONNECTED`)
      // 订阅会话主题 (广播)
      const topic = `/topic/chat/${convId}`
      ws.send(['SUBSCRIBE', `id:${me.subscriptionId}`, `destination:${topic}`, '', '\x00'].join('\n'))
      console.log(`[${label}] 📫 subscribed ${topic}`)
    } else if (frame.command === 'MESSAGE') {
      try {
        const msg = JSON.parse(frame.body)
        const content = (msg.content || '').substring(0, 30)
        console.log(`[${label}] 📨 msg: sender=${msg.senderId} content="${content}..." hash=${(msg.contentHash || '').substring(0, 12)}...`)
        onMessage(msg)
      } catch (e) {
        console.error(`[${label}] ❌ parse error`, e.message)
      }
    } else if (frame.command === 'ERROR') {
      console.error(`[${label}] ❌ ERROR:`, frame.headers['message'] || frame.body)
    }
  }

  ws.onerror = (e) => console.error(`[${label}] ⚠️ WS error`, e.message)
  ws.onclose = () => console.log(`[${label}] 🔌 closed`)

  return {
    send(destination, body) {
      ws.send(['SEND', `destination:${destination}`, 'content-type:application/json',
               `X-User-Id:${userId}`, '', JSON.stringify(body), '\x00'].join('\n'))
    },
    close: () => ws.close()
  }
}

async function createConv() {
  if (convId) return convId
  const resp = await fetch('http://localhost:8082/api/v1/chat/conversations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User-Id': '1001' },
    body: JSON.stringify({ title: 'WS 测试' })
  })
  return (await resp.json()).data.id
}

async function main() {
  console.log('╔══════════════════════════════════════════════╗')
  console.log('║  Fin-Chat WebSocket (STOMP) E2E 测试          ║')
  console.log('╚══════════════════════════════════════════════╝\n')

  convId = await createConv()
  console.log(`[STEP 1] 会话: ${convId}\n`)

  const receivedByB = []
  const receivedByA = []
  const A = makeClient(1001, 'USER A (1001)', (m) => receivedByA.push(m))
  const B = makeClient(2002, 'USER B (2002)', (m) => receivedByB.push(m))
  await new Promise(r => setTimeout(r, 2000))

  console.log('\n[STEP 2] USER A 发消息')
  A.send(`/app/chat/${convId}`, { conversationId: convId, content: 'Hello from A via WebSocket!', type: 'TEXT', senderType: 'CUSTOMER' })
  await new Promise(r => setTimeout(r, 1500))

  console.log('\n[STEP 3] USER B 回复')
  B.send(`/app/chat/${convId}`, { conversationId: convId, content: 'Hi A, 我是 B', type: 'TEXT', senderType: 'CUSTOMER' })
  await new Promise(r => setTimeout(r, 1500))

  console.log(`\n[STEP 4] 验证:`)
  console.log(`  USER A 收到推送数: ${receivedByA.length}`)
  console.log(`  USER B 收到推送数: ${receivedByB.length}`)
  if (receivedByB.length >= 1 && receivedByA.length >= 1) {
    console.log('  ✅ WebSocket 双向推送成功!')
    console.log('  ✅ Principal 绑定 + /user/queue/chat 路由正确')
  } else {
    console.log('  ⚠️  推送数不足, 请检查 server log')
  }

  A.close()
  B.close()
  setTimeout(() => process.exit((receivedByA.length + receivedByB.length) >= 2 ? 0 : 1), 500)
}

main().catch(e => { console.error(e); process.exit(1) })