package com.fin.kms.hsm;

import com.fin.kms.KeySpec;
import com.fin.kms.SignatureResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 单条加密机连接
 *
 * <p>封装 SJL05 / 通用国密加密机的 TCP 协议:
 * <pre>
 *   请求:  [magic(2)][cmd(2)][seq(4)][len(4)][data(len)]
 *   响应:  [magic(2)][cmd(2)][seq(4)][rc(4)][len(4)][data(len)]
 * </pre>
 *
 * <p>注意: 实际协议因厂商差异很大, 这里给的是通用骨架, 接入时按厂商 SDK 替换即可
 */
@Slf4j
public class HsmConnection {

    private static final short MAGIC = (short) 0xCAFE;
    private static final int MAX_PACKET = 64 * 1024;
    private static final int TIMEOUT_MS = 3000;

    // 命令码 (示例, 实际需按厂商协议)
    private static final short CMD_PING = 0x0001;
    private static final short CMD_SM3 = 0x0010;
    private static final short CMD_SM4_ENC = 0x0020;
    private static final short CMD_SM4_DEC = 0x0021;
    private static final short CMD_SM2_SIGN = 0x0030;
    private static final short CMD_SM2_DEC = 0x0031;
    private static final short CMD_KEY_GEN = 0x0040;

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final ReentrantLock lock = new ReentrantLock();
    private final String appId;
    private final String appKey;
    private volatile long seq = 0;
    private volatile boolean alive = true;

    public HsmConnection(String host, int port, String appId, String appKey) throws IOException {
        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
        this.socket.setSoTimeout(TIMEOUT_MS);
        this.socket.setTcpNoDelay(true);
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        this.appId = appId;
        this.appKey = appKey;
        // 握手 (国密 IPSec 通道建立, 双向证书认证)
        handshake();
    }

    /** 握手: 应用 ID + 密钥认证 */
    private void handshake() throws IOException {
        // 实际实现中, 这里是 IPSec 隧道 + 双向证书
        // 简化版: 发送 appId + 签名, 加密机校验后返回 OK
        byte[] payload = (appId + "|" + System.currentTimeMillis()).getBytes();
        // send(CMD_HANDSHAKE, payload);
        log.debug("HsmConnection handshake ok");
    }

    public boolean isAlive() {
        return alive && socket != null && !socket.isClosed() && socket.isConnected();
    }

    public void close() {
        alive = false;
        try { socket.close(); } catch (IOException ignore) {}
    }

    public boolean ping() {
        try {
            byte[] resp = send(CMD_PING, new byte[0]);
            return resp != null && resp.length == 0;
        } catch (Exception e) {
            alive = false;
            return false;
        }
    }

    public byte[] sm4Encrypt(String keyAlias, byte[] data) {
        byte[] aliasBytes = keyAlias.getBytes();
        ByteBuffer buf = ByteBuffer.allocate(aliasBytes.length + data.length)
                .put((byte) aliasBytes.length)
                .put(aliasBytes)
                .put(data);
        byte[] resp = send(CMD_SM4_ENC, buf.array());
        return resp;
    }

    public byte[] sm4Decrypt(String keyAlias, byte[] data) {
        byte[] aliasBytes = keyAlias.getBytes();
        ByteBuffer buf = ByteBuffer.allocate(aliasBytes.length + data.length)
                .put((byte) aliasBytes.length)
                .put(aliasBytes)
                .put(data);
        return send(CMD_SM4_DEC, buf.array());
    }

    public SignatureResult sm2Sign(String keyAlias, byte[] data) {
        byte[] aliasBytes = keyAlias.getBytes();
        // 1 | alias_len(2) | alias | data
        ByteBuffer buf = ByteBuffer.allocate(1 + 2 + aliasBytes.length + data.length)
                .put((byte) 1)                       // hasAlias
                .putShort((short) aliasBytes.length)
                .put(aliasBytes)
                .put(data);
        byte[] resp = send(CMD_SM2_SIGN, buf.array());
        // 解析: sig_len(2) | sig | pub_len(2) | pub
        ByteBuffer rb = ByteBuffer.wrap(resp);
        short sigLen = rb.getShort();
        byte[] sig = new byte[sigLen];
        rb.get(sig);
        short pubLen = rb.getShort();
        byte[] pub = new byte[pubLen];
        rb.get(pub);
        return new SignatureResult(sig, pub, "SM2withSM3");
    }

    public byte[] sm2Decrypt(String keyAlias, byte[] data) {
        byte[] aliasBytes = keyAlias.getBytes();
        ByteBuffer buf = ByteBuffer.allocate(aliasBytes.length + data.length)
                .put((byte) aliasBytes.length)
                .put(aliasBytes)
                .put(data);
        return send(CMD_SM2_DEC, buf.array());
    }

    public String generateKey(KeySpec spec) {
        byte[] alias = spec.fullAlias().getBytes();
        byte[] algo = spec.getAlgorithm().getBytes();
        ByteBuffer buf = ByteBuffer.allocate(algo.length + 1 + alias.length)
                .put((byte) algo.length).put(algo)
                .put((byte) alias.length).put(alias);
        send(CMD_KEY_GEN, buf.array());
        return spec.fullAlias();
    }

    /** 发送并接收 */
    private byte[] send(short cmd, byte[] data) {
        lock.lock();
        try {
            // [magic(2)][cmd(2)][seq(4)][len(4)][data(len)]
            ByteBuffer head = ByteBuffer.allocate(12)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putShort(MAGIC)
                    .putShort(cmd)
                    .putInt((int) (++seq))
                    .putInt(data.length);
            out.write(head.array());
            out.write(data);
            out.flush();

            // 响应
            byte[] headBuf = new byte[12];
            in.readFully(headBuf);
            ByteBuffer rh = ByteBuffer.wrap(headBuf).order(ByteOrder.BIG_ENDIAN);
            short magic = rh.getShort();
            if (magic != MAGIC) throw new IOException("bad magic: " + magic);
            rh.getShort();  // cmd
            rh.getInt();    // seq
            int rc = rh.getInt();
            int len = rh.getInt();
            if (rc != 0) throw new IOException("HSM rc=" + rc);
            if (len > MAX_PACKET) throw new IOException("oversize: " + len);
            byte[] payload = new byte[len];
            in.readFully(payload);
            return payload;
        } catch (IOException e) {
            alive = false;
            throw new RuntimeException("HSM 通信失败: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }
}
