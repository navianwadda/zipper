package com.livetvpro.app.utils

import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class Tls12SocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {

    private val tlsProtocols = arrayOf("TLSv1.2", "TLSv1.1", "TLSv1")

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket =
        patch(delegate.createSocket(s, host, port, autoClose))

    override fun createSocket(host: String, port: Int): Socket =
        patch(delegate.createSocket(host, port))

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        patch(delegate.createSocket(host, port, localHost, localPort))

    override fun createSocket(host: InetAddress, port: Int): Socket =
        patch(delegate.createSocket(host, port))

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        patch(delegate.createSocket(address, port, localAddress, localPort))

    private fun patch(socket: Socket): Socket {
        if (socket is SSLSocket) {
            val supported = socket.supportedProtocols.toSet()
            socket.enabledProtocols = tlsProtocols.filter { it in supported }.toTypedArray()
        }
        return socket
    }
}
