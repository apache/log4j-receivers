/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.log4j.net;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.BufferedInputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.lang.reflect.Method;

import org.apache.log4j.plugins.Plugin;
import org.apache.log4j.plugins.Receiver;
import org.apache.log4j.plugins.Pauseable;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.ComponentBase;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.Logger;

/**
  SocketHubReceiver receives a remote logging event on a configured
  socket and "posts" it to a LoggerRepository as if the event was
  generated locally. This class is designed to receive events from
  the SocketHubAppender class (or classes that send compatible events).

  <p>Once the event has been "posted", it will be handled by the
  appenders currently configured in the LoggerRespository.

  @author Mark Womack
  @author Ceki G&uuml;lc&uuml;
  @author Paul Smith (psmith@apache.org)
  @since 1.3
*/
public class SocketHubReceiver
extends Receiver implements SocketNodeEventListener, PortBased {

    /**
     * Default reconnection delay.
     */
  static final int DEFAULT_RECONNECTION_DELAY   = 30000;

    /**
     * Host.
     */
  protected String host;

    /**
     * Port.
     */
  protected int port;
    /**
     * Reconnection delay.
     */
  protected int reconnectionDelay = DEFAULT_RECONNECTION_DELAY;

    /**
     * Active.
     */
  protected boolean active = false;

    /**
     * Connector.
     */
  protected Connector connector;

    /**
     * Socket.
     */
  protected Socket socket;

    /**
     * Listener list.
     */
  private List listenerList = Collections.synchronizedList(new ArrayList());

    /**
     * Create new instance.
     */
  public SocketHubReceiver() {
     super();
  }

    /**
     * Create new instance.
     * @param h host
     * @param p port
     */
  public SocketHubReceiver(final String h,
                           final int p) {
    super();
    host = h;
    port = p;
  }

    /**
     * Create new instance.
     * @param h host
     * @param p port
     * @param repo logger repository
     */
  public SocketHubReceiver(final String h,
                           final int p,
                           final LoggerRepository repo) {
    super();
    host = h;
    port = p;
    repository = repo;
  }

  /**
   * Adds a SocketNodeEventListener to this receiver to be notified
   * of SocketNode events.
   * @param l listener
   */
  public void addSocketNodeEventListener(final SocketNodeEventListener l) {
    listenerList.add(l);
  }

  /**
   * Removes a specific SocketNodeEventListener from this instance
   * so that it will no  longer be notified of SocketNode events.
   * @param l listener
   */
  public void removeSocketNodeEventListener(
          final SocketNodeEventListener l) {
    listenerList.remove(l);
  }

  /**
    Get the remote host to connect to for logging events.
    @return host
   */
  public String getHost() {
    return host;
  }

  /**
   * Configures the Host property, this will require activateOptions
   * to be called for this to take effect.
   * @param remoteHost address of remote host.
   */
  public void setHost(final String remoteHost) {
    this.host = remoteHost;
  }
  /**
    Set the remote host to connect to for logging events.
   Equivalent to setHost.
   @param remoteHost address of remote host.
   */
  public void setPort(final String remoteHost) {
    host = remoteHost;
  }

  /**
    Get the remote port to connect to for logging events.
   @return port
   */
  public int getPort() {
    return port;
  }

  /**
    Set the remote port to connect to for logging events.
    @param p port
   */
  public void setPort(final int p) {
    this.port = p;
  }

  /**
     The <b>ReconnectionDelay</b> option takes a positive integer
     representing the number of milliseconds to wait between each
     failed connection attempt to the server. The default value of
     this option is 30000 which corresponds to 30 seconds.

     <p>Setting this option to zero turns off reconnection
     capability.
   @param delay milliseconds to wait or zero to not reconnect.
   */
  public void setReconnectionDelay(final int delay) {
    int oldValue = this.reconnectionDelay;
    this.reconnectionDelay = delay;
    firePropertyChange("reconnectionDelay", oldValue, this.reconnectionDelay);
  }

  /**
     Returns value of the <b>ReconnectionDelay</b> option.
   @return value of reconnection delay option.
   */
  public int getReconnectionDelay() {
    return reconnectionDelay;
  }

  /**
   * Returns true if the receiver is the same class and they are
   * configured for the same properties, and super class also considers
   * them to be equivalent. This is used by PluginRegistry when determining
   * if the a similarly configured receiver is being started.
   *
   * @param testPlugin The plugin to test equivalency against.
   * @return boolean True if the testPlugin is equivalent to this plugin.
   */
  public boolean isEquivalent(final Plugin testPlugin) {
    if (testPlugin != null && testPlugin instanceof SocketHubReceiver) {
      SocketHubReceiver sReceiver = (SocketHubReceiver) testPlugin;

      return (port == sReceiver.getPort()
              && host.equals(sReceiver.getHost())
              && reconnectionDelay == sReceiver.getReconnectionDelay()
              && super.isEquivalent(testPlugin));
    }
    return false;
  }

  /**
    Returns true if this receiver is active.
   @return true if receiver is active
   */
  public synchronized boolean isActive() {
    return active;
  }

  /**
    Sets the flag to indicate if receiver is active or not.
   @param b new value
   */
  protected synchronized void setActive(final boolean b) {
    active = b;
  }

  /**
    Starts the SocketReceiver with the current options. */
  public void activateOptions() {
    if (!isActive()) {
      setActive(true);
      fireConnector(false);
    }
  }

  /**
    Called when the receiver should be stopped. Closes the socket */
  public synchronized void shutdown() {
    // mark this as no longer running
    active = false;

    // close the socket
    try {
      if (socket != null) {
        socket.close();
      }
    } catch (Exception e) {
      // ignore for now
    }
    socket = null;

    // stop the connector
    if (connector != null) {
      connector.interrupted = true;
      connector = null;  // allow gc
    }
  }

  /**
    Listen for a socketClosedEvent from the SocketNode. Reopen the
    socket if this receiver is still active.
   @param e exception not used.
   */
  public void socketClosedEvent(final Exception e) {
    // we clear the connector object here
    // so that it actually does reconnect if the
    // remote socket dies.
    connector = null;
    fireConnector(true);
  }

    /**
     * Fire connectors.
     * @param isReconnect true if reconnect.
     */
  private synchronized void fireConnector(final boolean isReconnect) {
    if (active && connector == null) {
      getLogger().debug("Starting a new connector thread.");
      connector = new Connector(isReconnect);
      connector.setDaemon(true);
      connector.setPriority(Thread.MIN_PRIORITY);
      connector.start();
    }
  }

    /**
     * Set socket.
     * @param newSocket new value for socket.
     */
  private synchronized void setSocket(final Socket newSocket) {
    connector = null;
    socket = newSocket;
    SocketNode node = new SocketNode(socket, this);
    node.addSocketNodeEventListener(this);

    synchronized (listenerList) {
        for (Iterator iter = listenerList.iterator(); iter.hasNext();) {
            SocketNodeEventListener listener =
                    (SocketNodeEventListener) iter.next();
            node.addSocketNodeEventListener(listener);
        }
    }
    new Thread(node).start();
  }

  /**
   The Connector will reconnect when the server becomes available
   again.  It does this by attempting to open a new connection every
   <code>reconnectionDelay</code> milliseconds.

   <p>It stops trying whenever a connection is established. It will
   restart to try reconnect to the server when previpously open
   connection is droppped.

   @author  Ceki G&uuml;lc&uuml;
   */
  private final class Connector extends Thread {

      /**
       * Interruption status.
       */
    boolean interrupted = false;
      /**
       * If true, then delay on next iteration.
       */
    boolean doDelay;

      /**
       * Create new instance.
       * @param isReconnect true if reconnecting.
       */
    public Connector(final boolean isReconnect) {
      super();
      doDelay = isReconnect;
    }

      /**
       * Attempt to connect until interrupted.
       */
    public void run() {
      while (!interrupted) {
        try {
          if (doDelay) {
            getLogger().debug("waiting for " + reconnectionDelay
              + " milliseconds before reconnecting.");
            sleep(reconnectionDelay);
          }
          doDelay = true;
          getLogger().debug("Attempting connection to " + host);
          Socket s = new Socket(host, port);
          setSocket(s);
          getLogger().debug(
                  "Connection established. Exiting connector thread.");
          break;
        } catch (InterruptedException e) {
          getLogger().debug("Connector interrupted. Leaving loop.");
          return;
        } catch (java.net.ConnectException e) {
          getLogger().debug("Remote host {} refused connection.", host);
        } catch (IOException e) {
          getLogger().debug("Could not connect to {}. Exception is {}.",
                  host, e);
        }
      }
    }
  }

    /**
     * This method does nothing.
     * @param remoteInfo remote info.
     */
  public void socketOpened(final String remoteInfo) {

    // This method does nothing.
  }

    /**
       Read {@link org.apache.log4j.spi.LoggingEvent}
     objects sent from a remote client using
       Sockets (TCP). These logging events are logged according to local
       policy, as if they were generated locally.

       <p>For example, the socket node might decide to log events to a
       local file and also resent them to a second socket node.

        This class is a replica of org.apache.log4j.net.SocketNode
        from log4j 1.3 which is substantially different from
        the log4j 1.2 implementation.  The class was made a
        member of SocketHubReceiver to avoid potential collisions.

        @author  Ceki G&uuml;lc&uuml;
        @author  Paul Smith (psmith@apache.org)
    */
    private static final class SocketNode
            extends ComponentBase implements Runnable, Pauseable {

        /**
         * Paused state.
         */
      private boolean paused;
        /**
         * Socket.
         */
      private Socket socket;
        /**
         * Receiver.
         */
      private Receiver receiver;
        /**
         * List of listeners.
         */
      private List listenerList = Collections.synchronizedList(new ArrayList());

        /**
         * Method descriptor for LoggingEvent.setProperty
         *   which does not exist in log4j 1.2.14.
         */
      private static final Method LOGGING_EVENT_SET_PROPERTY =
              getLoggingEventSetProperty();

        /**
         * Get method descriptor for LoggingEvent.setProperty
         *   which does not exist in log4j 1.2.14.
         * @return method descriptor or null if not supported.
         */
      private static Method getLoggingEventSetProperty() {
          Method m = null;
          try {
              m = LoggingEvent.class.getMethod("setProperty",
                              new Class[] {
                                      String.class, String.class
                              });
          } catch (NoSuchMethodException e) {
              return null;
          }
          return m;
      }

      /**
        Constructor for socket and logger repository.
       @param s socket
       @param hierarchy logger repository
       */
      public SocketNode(final Socket s,
                        final LoggerRepository hierarchy) {
        super();
        this.socket = s;
        this.repository = hierarchy;
      }

      /**
        Constructor for socket and receiver.
       @param s socket
       @param r receiver
       */
      public SocketNode(final Socket s, final Receiver r) {
        super();
        this.socket = s;
        this.receiver = r;
      }

      /**
       * Set the event listener on this node.
       *
       * @deprecated Now supports mutliple listeners, this method
       * simply invokes the removeSocketNodeEventListener() to remove
       * the listener, and then readds it.
       * @param l listener
       */
      public void setListener(final SocketNodeEventListener l) {
        removeSocketNodeEventListener(l);
        addSocketNodeEventListener(l);
      }

      /**
       * Adds the listener to the list of listeners to be notified of the
       * respective event.
       * @param listener the listener to add to the list
       */
      public void addSocketNodeEventListener(
              final SocketNodeEventListener listener) {
        listenerList.add(listener);
      }

      /**
       * Removes the registered Listener from this instances list of
       * listeners.  If the listener has not been registered, then invoking
       * this method has no effect.
       *
       * @param listener the SocketNodeEventListener to remove
       */
      public void removeSocketNodeEventListener(
              final SocketNodeEventListener listener) {
        listenerList.remove(listener);
      }

        /**
         * Set property in event.
         * @param event event, may not be null.
         * @param propName property name
         * @param propValue property value
         * @return true if property was set
         */
      private static boolean setEventProperty(
              final LoggingEvent event,
              final String propName,
              final String propValue) {
          if (LOGGING_EVENT_SET_PROPERTY != null) {
              try {
                  LOGGING_EVENT_SET_PROPERTY.invoke(event,
                          new Object[] {
                                  propName, propValue
                          });
                  return true;
              } catch (Exception e) {
                  return false;
              }
          }
          return false;
      }

        /**
         * Deserialize events from socket until interrupted.
         */
      public void run() {
        LoggingEvent event;
        Logger remoteLogger;
        Exception listenerException = null;
        ObjectInputStream ois = null;

        try {
          ois =
            new ObjectInputStream(
              new BufferedInputStream(socket.getInputStream()));
        } catch (Exception e) {
          ois = null;
          listenerException = e;
          getLogger().error(
                  "Exception opening ObjectInputStream to " + socket, e);
        }

        if (ois != null) {
          String remoteInfo =
            socket.getInetAddress().getHostName() + ":" + socket.getPort();

          /**
           * notify the listener that the socket has been
           * opened and this SocketNode is ready and waiting
           */
          fireSocketOpened(remoteInfo);

          try {
            while (true) {
              // read an event from the wire
              event = (LoggingEvent) ois.readObject();

              // store the known remote info in an event property
              setEventProperty(event, "log4j.remoteSourceInfo", remoteInfo);

              // if configured with a receiver, tell it to post the event
              if (!isPaused()) {
                if ((receiver != null)) {
                  receiver.doPost(event);

                  // else post it via the hierarchy
                } else {
                  // get a logger from the hierarchy. The name of the logger
                  // is taken to be the name contained in the event.
                  remoteLogger = repository.getLogger(event.getLoggerName());

                  //event.logger = remoteLogger;
                  // apply the logger-level filter
                  if (event
                    .getLevel()
                    .isGreaterOrEqual(remoteLogger.getEffectiveLevel())) {
                    // finally log the event as if was generated locally
                    remoteLogger.callAppenders(event);
                  }
                }
              } else {
                //we simply discard this event.
              }
            }
          } catch (java.io.EOFException e) {
            getLogger().info("Caught java.io.EOFException closing connection.");
            listenerException = e;
          } catch (java.net.SocketException e) {
            getLogger().info(
                    "Caught java.net.SocketException closing connection.");
            listenerException = e;
          } catch (IOException e) {
            getLogger().info("Caught java.io.IOException: " + e);
            getLogger().info("Closing connection.");
            listenerException = e;
          } catch (Exception e) {
            getLogger().error("Unexpected exception. Closing connection.", e);
            listenerException = e;
          }
        }

        // close the socket
        try {
          if (ois != null) {
            ois.close();
          }
        } catch (Exception e) {
          //getLogger().info("Could not close connection.", e);
        }

        // send event to listener, if configured
        if (listenerList.size() > 0) {
          fireSocketClosedEvent(listenerException);
        }
      }

      /**
       * Notifies all registered listeners regarding the closing of the Socket.
       * @param listenerException listener exception
       */
      private void fireSocketClosedEvent(final Exception listenerException) {
        synchronized (listenerList) {
            for (Iterator iter = listenerList.iterator(); iter.hasNext();) {
                SocketNodeEventListener snel =
                        (SocketNodeEventListener) iter.next();
                if (snel != null) {
                    snel.socketClosedEvent(listenerException);
                }
            }
        }
      }

      /**
       * Notifies all registered listeners regarding the opening of a Socket.
       * @param remoteInfo remote info
       */
      private void fireSocketOpened(final String remoteInfo) {
        synchronized (listenerList) {
            for (Iterator iter = listenerList.iterator(); iter.hasNext();) {
                SocketNodeEventListener snel =
                        (SocketNodeEventListener) iter.next();
                if (snel != null) {
                    snel.socketOpened(remoteInfo);
                }
            }
        }
      }

        /**
         * Sets if node is paused.
         * @param b new value
         */
      public void setPaused(final boolean b) {
        this.paused = b;
      }

        /**
         * Get if node is paused.
         * @return true if pause.
         */
      public boolean isPaused() {
        return this.paused;
      }
    }

}
