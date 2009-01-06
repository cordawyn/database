package com.bigdata.service.jini;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.discovery.LookupLocatorDiscovery;

import org.apache.log4j.Logger;
import org.apache.zookeeper.ZooKeeper;

import com.bigdata.util.concurrent.DaemonThreadFactory;
import com.bigdata.zookeeper.ZookeeperClientConfig;

/**
 * A helper class that starts all the necessary services for a Jini federation.
 * This is used when testing, but NOT for benchmarking performance. For
 * benchmarking you MUST connect to an existing federation, ideally one deployed
 * over a cluster of machines!
 * <p>
 * Note: You MUST specify a sufficiently lax security policy. Also, you
 * typically will want to enable NIO. For example
 * 
 * <pre>
 * -Djava.security.policy=policy.all -Dcom.sun.jini.jeri.tcp.useNIO=true
 * </pre>
 * 
 * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan Thompson</a>
 * @version $Id$
 */
public class JiniServicesHelper {

    protected final static Logger log = Logger
            .getLogger(JiniServicesHelper.class);

    public MetadataServer metadataServer0;

    public DataServer dataServer1;

    public DataServer dataServer0;

    public LoadBalancerServer loadBalancerServer0;

    public TransactionServer transactionServer0;

    public ResourceLockServer resourceLockServer0;

    public JiniClient client;

    public ZooKeeper zookeeper;
    
    public ZookeeperClientConfig zooConfig;
    
    /**
     * Looks for configuration files in the directory identified by the path
     * and starts the various services required by a {@link JiniFederation}.
     * This class assumes that the following configuration files will exist
     * in the directory identified by <i>path</i>.
     * 
     * <ul>
     * <li>ResourceLockServer0.config</li>
     * <li>MetadataServer0.config</li>
     * <li>DataServer0.config</li>
     * <li>DataServer1.config</li>
     * <li>LoadBalancerServer0.config</li>
     * <li>TransactionServer0.config</li>
     * <li>Client.config</li>
     * </ul>
     * 
     * @param path
     *            The path to the configuration files.  The path must include
     *            a trailing separator character.
     */
    public JiniServicesHelper(String path) {

        this.path = path;

    }

    private final String path;

    private ExecutorService threadPool = Executors
            .newCachedThreadPool(new DaemonThreadFactory
                    (getClass().getName()+".threadPool"));
    
    /**
     * Starts all services and connects the {@link JiniClient} to the
     * federation.
     * 
     * @throws RuntimeException
     *             if something goes wrong.
     */
    public void start() {

        // @todo verify that this belongs here vs in a main(String[]).
        System.setSecurityManager(new SecurityManager());

        final String[] options = new String[] {
                "com.bigdata.zookeeper.zroot = \"/bigdata-standalone\"",
                "com.bigdata.zookeeper.servers = \"localhost:2181\""
        };
        
        /*
         * Start up a resource lock server.
         */
        threadPool
                .execute(resourceLockServer0 = new ResourceLockServer(concat(
                        new String[] { path + "ResourceLockServer0.config" },
                        options)));

        /*
         * Start up a timestamp server.
         */
        threadPool.execute(transactionServer0 = new TransactionServer(concat(
                new String[] { path + "TransactionServer0.config" }, options)));

        /*
         * Start up a data server before the metadata server so that we can make
         * sure that it is detected by the metadata server once it starts up.
         */
        threadPool.execute(dataServer1 = new DataServer(concat(
                new String[] { path + "DataServer1.config" }, options)));

        /*
         * Start up a load balancer server.
         */
        threadPool
                .execute(loadBalancerServer0 = new LoadBalancerServer(concat(
                        new String[] { path + "LoadBalancerServer0.config" },
                        options)));

        /*
         * Start the metadata server.
         */
        threadPool.execute(metadataServer0 = new MetadataServer(concat(
                new String[] { path + "MetadataServer0.config" }, options)));

        /*
         * Start up a data server after the metadata server so that we can make
         * sure that it is detected by the metadata server once it starts up.
         */
        threadPool.execute(dataServer0 = new DataServer(concat(
                new String[] { path + "DataServer0.config" }, options)));

        client = JiniClient.newInstance(concat(new String[] { path
                + "Client.config" }, options));

        // connect the client - this will get discovery running.
        final JiniFederation fed = client.connect();

        zookeeper = fed.getZookeeper();
        zooConfig = fed.getZooConfig();
        
        // Wait until all the services are up.
        getServiceID(resourceLockServer0);
        getServiceID(transactionServer0);
        getServiceID(metadataServer0);
        getServiceID(dataServer0);
        getServiceID(dataServer1);
        getServiceID(loadBalancerServer0);

    }

    /**
     * Immediate shutdown.
     */
    public void shutdown() {

        if (client != null && client.isConnected()) {

            client.disconnect(true/* immediateShutdown */);

            client = null;

        }
        
        threadPool.shutdownNow();

    }

    /**
     * Shuts down and <em>destroys</em> the services in the federation. The
     * shutdown is abrubt. You can expect to see messages about interrupted IO
     * such as
     * 
     * <pre>
     * java.rmi.MarshalException: error marshalling arguments; nested exception is: 
     *     java.io.IOException: request I/O interrupted
     *     at net.jini.jeri.BasicInvocationHandler.invokeRemoteMethodOnce(BasicInvocationHandler.java:785)
     *     at net.jini.jeri.BasicInvocationHandler.invokeRemoteMethod(BasicInvocationHandler.java:659)
     *     at net.jini.jeri.BasicInvocationHandler.invoke(BasicInvocationHandler.java:528)
     *     at $Proxy5.notify(Ljava.lang.String;Ljava.util.UUID;Ljava.lang.String;[B)V(Unknown Source)
     * </pre>
     * 
     * These messages can be safely ignored IF they occur during this method.
     */
    public void destroy() {

        if (zookeeper != null && zooConfig != null) {

            try {

                // clear out everything in zookeeper for this federation.
                zookeeper.delete(zooConfig.zroot, -1/* version */);
                
            } catch (Exception e) {
                
                // ignore.
                log.warn("zroot=" + zooConfig.zroot + " : "
                        + e.getLocalizedMessage(), e);
                
            }
            
        }

        if (client != null && client.isConnected()) {

            client.disconnect(true/* immediateShutdown */);

            client = null;

        }

        if (metadataServer0 != null) {

            metadataServer0.destroy();

            metadataServer0 = null;

        }

        if (dataServer0 != null) {

            dataServer0.destroy();

            dataServer0 = null;

        }

        if (dataServer1 != null) {

            dataServer1.destroy();

            dataServer1 = null;

        }

        if (loadBalancerServer0 != null) {

            loadBalancerServer0.destroy();

            loadBalancerServer0 = null;

        }

        if (transactionServer0 != null) {

            transactionServer0.destroy();

            transactionServer0 = null;

        }

        if (resourceLockServer0 != null) {

            resourceLockServer0.destroy();

            resourceLockServer0 = null;

        }

    }

    /**
     * Return the {@link ServiceID} of a server that we started ourselves.
     * The method waits until the {@link ServiceID} becomes available on
     * {@link AbstractServer#getServiceID()}.
     * 
     * @throws RuntimeException
     *                If the {@link ServiceID} can not be found after a
     *                timeout.
     * 
     * @throws RuntimeException
     *                if the thread is interrupted while it is waiting to
     *                retry.
     */
    static private ServiceID getServiceID(AbstractServer server) {

        ServiceID serviceID = null;

        for (int i = 0; i < 20 && serviceID == null; i++) {

            /*
             * Note: This can be null since the serviceID is not assigned
             * synchonously by the registrar.
             */

            serviceID = server.getServiceID();

            if (serviceID == null) {

                /*
                 * We wait a bit and retry until we have it or timeout.
                 */

                try {

                    Thread.sleep(200);

                } catch (InterruptedException e) {

                    throw new RuntimeException("Interrupted: " + e, e);

                }

            }

        }

        if (serviceID == null)
            throw new RuntimeException("Server did not start? "+server);

        return serviceID;

    }

    /**
     * Return <code>true</code> if Jini appears to be running on the
     * localhost.
     * 
     * @throws Exception
     */
    public static boolean isJiniRunning() {
        
        return isJiniRunning(new String[] { "jini://localhost/" });
        
    }
    
    /**
     * @param url
     *            One or more unicast URIs of the form <code>jini://host/</code>
     *            or <code>jini://host:port/</code> -or- an empty array if you
     *            want to use <em>multicast</em> discovery.
     */
    public static boolean isJiniRunning(String[] url) {
        
        final LookupLocator[] locators = new LookupLocator[url.length];

        for (int i = 0; i < url.length; i++) {
           
            try {

                locators[i] = new LookupLocator("jini://localhost/");

            } catch (MalformedURLException e) {

                throw new RuntimeException(e);

            }
            
        }

        final LookupLocatorDiscovery discovery = new LookupLocatorDiscovery(
                locators);

        try {

            final long timeout = 2000; // ms

            final long begin = System.currentTimeMillis();

            long elapsed;

            while ((elapsed = (System.currentTimeMillis() - begin)) < timeout) {

                final ServiceRegistrar[] registrars = discovery.getRegistrars();

                if (registrars.length > 0) {

                    if(log.isInfoEnabled())
                        log.info("Found " + registrars.length
                                + " registrars in " + elapsed + "ms.");

                    return true;

                }

            }

            log
                    .error("Could not find any service registrars on localhost after "
                            + elapsed + " ms");

            return false;

        } finally {

            discovery.terminate();

        }

    }

    /**
     * Combines the two arrays, appending the contents of the 2nd array to the
     * contents of the first array.
     * 
     * @param a
     * @param b
     * @return
     */
    @SuppressWarnings("unchecked")
    protected static <T> T[] concat(final T[] a, final T[] b) {

        final T[] c = (T[]) java.lang.reflect.Array.newInstance(a.getClass()
                .getComponentType(), a.length + b.length);

        // final String[] c = new String[a.length + b.length];

        System.arraycopy(a, 0, c, 0, a.length);

        System.arraycopy(b, 0, c, a.length, b.length);

        return c;

    }

}
