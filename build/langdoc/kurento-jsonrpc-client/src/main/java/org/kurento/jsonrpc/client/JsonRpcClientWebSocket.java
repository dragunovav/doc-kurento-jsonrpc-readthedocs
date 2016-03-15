/*
 * (C) Copyright 2013 Kurento (http://kurento.org/)
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 */

package org.kurento.jsonrpc.client;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

public class JsonRpcClientWebSocket extends AbstractJsonRpcClientWebSocket {

  private static final int MAX_PACKET_SIZE = 1000000;

  @WebSocket
  public class WebSocketClientSocket {

    @OnWebSocketClose
    public void onClose(int statusCode, String closeReason) {
      log.debug("Websocket disconnected because '{}' (status code {})", closeReason, statusCode);
      handleReconnectDisconnection(statusCode, closeReason);
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
    }

    @OnWebSocketMessage
    public void onMessage(String message) {
      receivedTextMessage(message);
    }
  }

  protected final SslContextFactory sslContextFactory;

  protected volatile Session jettyWsSession;
  protected volatile WebSocketClient jettyClient;

  public JsonRpcClientWebSocket(String url) {
    this(url, null, new SslContextFactory());
  }

  public JsonRpcClientWebSocket(String url, SslContextFactory sslContextFactory) {
    this(url, null, sslContextFactory);
  }

  public JsonRpcClientWebSocket(String url, JsonRpcWSConnectionListener connectionListener) {
    this(url, connectionListener, new SslContextFactory());
  }

  public JsonRpcClientWebSocket(String url, JsonRpcWSConnectionListener connectionListener,
      SslContextFactory sslContextFactory) {
    super(url, connectionListener);
    this.sslContextFactory = sslContextFactory;
  }

  @Override
  protected void sendTextMessage(String jsonMessage) throws IOException {

    if (jettyWsSession == null) {
      throw new IllegalStateException(
          label + " JsonRpcClient is disconnected from WebSocket server at '" + this.url + "'");
    }

    synchronized (jettyWsSession) {
      jettyWsSession.getRemote().sendString(jsonMessage);
    }
  }

  protected boolean isNativeClientConnected() {
    return jettyWsSession != null && jettyWsSession.isOpen();
  }

  protected void connectNativeClient() throws TimeoutException, Exception {

    if (jettyClient == null || jettyClient.isStopped() || jettyClient.isStopping()) {

      jettyClient = new WebSocketClient(sslContextFactory);
      jettyClient.setConnectTimeout(this.connectionTimeout);
      WebSocketPolicy policy = jettyClient.getPolicy();
      policy.setMaxBinaryMessageBufferSize(MAX_PACKET_SIZE);
      policy.setMaxTextMessageBufferSize(MAX_PACKET_SIZE);
      policy.setMaxBinaryMessageSize(MAX_PACKET_SIZE);
      policy.setMaxTextMessageSize(MAX_PACKET_SIZE);

      jettyClient.start();

    }

    WebSocketClientSocket socket = new WebSocketClientSocket();
    ClientUpgradeRequest request = new ClientUpgradeRequest();

    jettyWsSession = jettyClient.connect(socket, new URI(url), request).get(this.connectionTimeout,
        TimeUnit.MILLISECONDS);

    jettyWsSession.setIdleTimeout(this.idleTimeout);

  }

  @Override
  public void closeNativeClient() {
    if (jettyClient != null) {
      log.debug("{} Closing client", label);
      try {
        jettyClient.stop();
        jettyClient.destroy();
      } catch (Exception e) {
        log.debug("{} Could not properly close websocket client. Reason: {}", label,
            e.getMessage());
      }
      jettyClient = null;
    }

    if (jettyWsSession != null) {
      jettyWsSession.close();
      jettyWsSession = null;
    } else {
      log.warn("{} Trying to close a JsonRpcClientWebSocket with jettyWsSession=null", label);
    }
  }

}
