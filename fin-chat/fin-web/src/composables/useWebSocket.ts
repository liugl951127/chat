import { ref, onUnmounted } from 'vue'
import { Client, IMessage } from '@stomp/stompjs'

export function useWebSocket(onMessage: (msg: any) => void) {
  const connected = ref(false)
  let client: Client | null = null

  const connect = (token: string) => {
    client = new Client({
      brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/chat`,
      connectHeaders: { Authorization: `Bearer ${token}` },
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      reconnectDelay: 5000,
      onConnect: () => {
        connected.value = true
        client!.subscribe('/user/queue/chat', (m: IMessage) => {
          try {
            onMessage(JSON.parse(m.body))
          } catch (e) {
            console.error('WS parse fail', e)
          }
        })
      },
      onDisconnect: () => { connected.value = false },
      onStompError: (frame) => {
        console.error('STOMP error', frame.headers['message'])
      }
    })
    client.activate()
  }

  const send = (destination: string, body: any) => {
    client?.publish({ destination, body: JSON.stringify(body) })
  }

  const disconnect = () => client?.deactivate()

  onUnmounted(disconnect)

  return { connected, connect, send, disconnect }
}
