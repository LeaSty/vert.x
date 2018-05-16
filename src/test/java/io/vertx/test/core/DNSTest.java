/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.core;

import static io.vertx.test.core.TestUtils.assertIllegalStateException;
import static io.vertx.test.core.TestUtils.assertNullPointerException;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.Function;

import org.apache.directory.server.dns.messages.DnsMessage;
import org.junit.Test;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.DnsClientOptions;
import io.vertx.core.dns.DnsException;
import io.vertx.core.dns.DnsResponseCode;
import io.vertx.core.dns.MxRecord;
import io.vertx.core.dns.SrvRecord;
import io.vertx.core.dns.impl.DnsClientImpl;
import io.vertx.test.fakedns.FakeDNSServer;
import io.vertx.test.netty.TestLoggerFactory;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DNSTest extends VertxTestBase {

  private FakeDNSServer dnsServer;

  @Test
  public void testIllegalArguments() throws Exception {
    DnsClient dns = prepareDns(FakeDNSServer.testResolveAAAA("::1"));

    assertNullPointerException(() -> dns.lookup(null, ar -> {}));
    assertNullPointerException(() -> dns.lookup4(null, ar -> {}));
    assertNullPointerException(() -> dns.lookup6(null, ar -> {}));
    assertNullPointerException(() -> dns.resolveA(null, ar -> {}));
    assertNullPointerException(() -> dns.resolveAAAA(null, ar -> {}));
    assertNullPointerException(() -> dns.resolveCNAME(null, ar -> {}));
    assertNullPointerException(() -> dns.resolveMX(null, ar -> {}));
    assertNullPointerException(() -> dns.resolveTXT(null, ar -> {}));
    assertNullPointerException(() -> dns.resolvePTR(null, ar -> {}));
    assertNullPointerException(() -> dns.resolveNS(null, ar -> {}));
    assertNullPointerException(() -> dns.resolveSRV(null, ar -> {}));

    dnsServer.stop();
  }

  @Test
  public void testDefaultDnsClient() throws Exception {
    testDefaultDnsClient(vertx -> vertx.createDnsClient());
  }

  @Test
  public void testDefaultDnsClientWithOptions() throws Exception {
    testDefaultDnsClient(vertx -> vertx.createDnsClient(new DnsClientOptions()));
  }

  private void testDefaultDnsClient(Function<Vertx, DnsClient> clientProvider) throws Exception {
    final String ip = "10.0.0.1";
    FakeDNSServer fakeDNSServer = FakeDNSServer.testLookup4(ip);
    fakeDNSServer.start();
    VertxOptions vertxOptions = new VertxOptions();
    InetSocketAddress fakeServerAddress = fakeDNSServer.localAddress();
    vertxOptions.getAddressResolverOptions().addServer(fakeServerAddress.getHostString() + ":" + fakeServerAddress.getPort());
    Vertx vertxWithFakeDns = Vertx.vertx(vertxOptions);
    DnsClient dnsClient = clientProvider.apply(vertxWithFakeDns);

    dnsClient.lookup4("vertx.io", onSuccess(result -> {
      assertEquals(ip, result);
      testComplete();
    }));
    await();
    fakeDNSServer.stop();
    vertxWithFakeDns.close();
  }

  @Test
  public void testResolveA() throws Exception {
    final String ip = "10.0.0.1";
    DnsClient dns = prepareDns(FakeDNSServer.testResolveA(ip));

    dns.resolveA("vertx.io", onSuccess(result -> {
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      assertEquals(ip, result.get(0));
      ((DnsClientImpl) dns).inProgressQueries(num -> {
        assertEquals(0, (int)num);
        testComplete();
      });
    }));
    await();
    dnsServer.stop();
  }

  @Test
  public void testResolveAAAA() throws Exception {
    DnsClient dns = prepareDns(FakeDNSServer.testResolveAAAA("::1"));

    dns.resolveAAAA("vertx.io", onSuccess(result -> {
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      assertEquals("0:0:0:0:0:0:0:1", result.get(0));
      testComplete();
    }));
    await();
    dnsServer.stop();
  }

  @Test
  public void testResolveMX() throws Exception {
    final String mxRecord = "mail.vertx.io";
    final int prio = 10;
    DnsClient dns = prepareDns(FakeDNSServer.testResolveMX(prio, mxRecord));

    dns.resolveMX("vertx.io", onSuccess(result -> {
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      MxRecord record = result.get(0);
      assertEquals(prio, record.priority());
      assertEquals(record.name(), mxRecord);
      testComplete();
    }));
    await();
    dnsServer.stop();
  }

  @Test
  public void testResolveTXT() throws Exception {
    final String txt = "vertx is awesome";
    DnsClient dns = prepareDns(FakeDNSServer.testResolveTXT(txt));
    dns.resolveTXT("vertx.io", onSuccess(result -> {
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      assertEquals(txt, result.get(0));
      testComplete();
    }));
    await();
    dnsServer.stop();
  }

  @Test
  public void testResolveNS() throws Exception {
    final String ns = "ns.vertx.io";
    DnsClient dns = prepareDns(FakeDNSServer.testResolveNS(ns));

    dns.resolveNS("vertx.io", onSuccess(result -> {
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      assertEquals(ns, result.get(0));
      testComplete();
    }));
    await();
    dnsServer.stop();
  }

  @Test
  public void testResolveCNAME() throws Exception {
    final String cname = "cname.vertx.io";
    DnsClient dns = prepareDns(FakeDNSServer.testResolveCNAME(cname));

    dns.resolveCNAME("vertx.io", onSuccess(result -> {
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      String record = result.get(0);
      assertFalse(record.isEmpty());
      assertEquals(cname, record);
      testComplete();
    }));
    await();
    dnsServer.stop();
  }

  @Test
  public void testResolvePTR() throws Exception {
    final String ptr = "ptr.vertx.io";
    DnsClient dns = prepareDns(FakeDNSServer.testResolvePTR(ptr));

    dns.resolvePTR("10.0.0.1.in-addr.arpa", onSuccess(result -> {
      assertEquals(ptr, result);
      testComplete();
    }));
    await();
    dnsServer.stop();
  }

  @Test
  public void testResolveSRV() throws Exception {
    final int priority = 10;
    final int weight = 1;
    final int port = 80;
    final String target = "vertx.io";

    DnsClient dns = prepareDns(FakeDNSServer.testResolveSRV(priority, weight, port, target));

    dns.resolveSRV("vertx.io", ar -> {
      List<SrvRecord> result = ar.result();
      assertNotNull(result);
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());

      SrvRecord record = result.get(0);

      assertEquals(priority, record.priority());
      assertEquals(weight, record.weight());
      assertEquals(port, record.port());
      assertEquals(target, record.target());

      testComplete();
    });
    await();
    dnsServer.stop();
  }

  @Test
  public void testLookup4() throws Exception {
    final String ip = "10.0.0.1";
    FakeDNSServer server = FakeDNSServer.testLookup4(ip);
    DnsClient dns = prepareDns(server);
    dns.lookup4("vertx.io", onSuccess(result -> {
      assertEquals(ip, result);
      DnsMessage msg = server.pollMessage();
      assertTrue(msg.isRecursionDesired());
      testComplete();
    }));
    await();
    dnsServer.stop();
  }

  @Test
  public void testLookup6() throws Exception {
    DnsClient dns = prepareDns(FakeDNSServer.testLookup6());

    dns.lookup6("vertx.io", onSuccess(result -> {
      assertEquals("0:0:0:0:0:0:0:1", result);
      testComplete();
    }));
    await();
    dnsServer.stop();
  }

  @Test
  public void testLookup() throws Exception {
    final String ip = "10.0.0.1";
    DnsClient dns = prepareDns(FakeDNSServer.testLookup(ip));

    dns.lookup("vertx.io", onSuccess(result -> {
      assertEquals(ip, result);
      testComplete();
    }));
    await();
    dnsServer.stop();
  }

  @Test
  public void testTimeout() throws Exception {
    DnsClient dns = vertx.createDnsClient(new DnsClientOptions().setHost("localhost").setPort(10000).setQueryTimeout(5000));

    dns.lookup("vertx.io", onFailure(result -> {
      assertEquals(VertxException.class, result.getClass());
      assertEquals("DNS query timeout for vertx.io", result.getMessage());
      ((DnsClientImpl) dns).inProgressQueries(num -> {
        assertEquals(0, (int)num);
        testComplete();
      });
    }));
    await();
  }

  @Test
  public void testLookupNonExisting() throws Exception {
    DnsClient dns = prepareDns(FakeDNSServer.testLookupNonExisting());
    dns.lookup("gfegjegjf.sg1", ar -> {
      DnsException cause = (DnsException)ar.cause();
      assertEquals(DnsResponseCode.NXDOMAIN, cause.code());
      testComplete();
    });
    await();
    dnsServer.stop();
  }

  @Test
  public void testReverseLookupIpv4() throws Exception {
    String address = "10.0.0.1";
    final String ptr = "ptr.vertx.io";
    DnsClient dns = prepareDns(FakeDNSServer.testReverseLookup(ptr));

    dns.reverseLookup(address, onSuccess(result -> {
      assertEquals(ptr, result);
      testComplete();
    }));
    await();
    dnsServer.stop();
  }

  @Test
  public void testReverseLookupIpv6() throws Exception {
    final String ptr = "ptr.vertx.io";

    DnsClient dns = prepareDns(FakeDNSServer.testReverseLookup(ptr));

    dns.reverseLookup("::1", onSuccess(result -> {
      assertEquals(ptr, result);
      testComplete();
    }));
    await();
    dnsServer.stop();
  }

  @Test
  public void testUseInMultithreadedWorker() throws Exception {
    class MyVerticle extends AbstractVerticle {
      @Override
      public void start() {
        assertIllegalStateException(() -> vertx.createDnsClient(1234, "localhost"));
        testComplete();
      }
    }
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, new DeploymentOptions().setWorker(true).setMultiThreaded(true));
    await();
  }

  @Test
  public void testLookup4CNAME() throws Exception {
    final String cname = "cname.vertx.io";
    final String ip = "10.0.0.1";
    DnsClient dns = prepareDns(FakeDNSServer.testLookup4CNAME(cname, ip));

    dns.lookup4("vertx.io", onSuccess(result -> {
      assertEquals(ip, result);
      testComplete();
    }));
    await();
    dnsServer.stop();
  }

  private TestLoggerFactory testLogging(DnsClientOptions options) {
    final String ip = "10.0.0.1";
    return TestUtils.testLogging(() -> {
	  try {
		prepareDns(FakeDNSServer.testResolveA(ip), options)
        .resolveA(ip, fut -> {
        	testComplete();
        });
	    await();
	  } catch (Exception e) {
		fail(e);
	  }
    });
  }
  
  @Test
  public void testLogActivity() throws Exception {
    TestLoggerFactory factory = testLogging(new DnsClientOptions().setLogActivity(true));
    assertTrue(factory.hasName("io.netty.handler.logging.LoggingHandler"));
    dnsServer.stop();
  }

  @Test
  public void testDoNotLogActivity() throws Exception {
    TestLoggerFactory factory = testLogging(new DnsClientOptions().setLogActivity(false));
    assertFalse(factory.hasName("io.netty.handler.logging.LoggingHandler"));
    dnsServer.stop();
  }

  private DnsClient prepareDns(FakeDNSServer server) throws Exception {
    return prepareDns(server, new DnsClientOptions().setQueryTimeout(15000));
  }

  private DnsClient prepareDns(FakeDNSServer server, DnsClientOptions options) throws Exception {
    dnsServer = server;
    dnsServer.start();
    InetSocketAddress addr = (InetSocketAddress) dnsServer.getTransports()[0].getAcceptor().getLocalAddress();
    return vertx.createDnsClient(new DnsClientOptions(options).setPort(addr.getPort()).setHost(addr.getAddress().getHostAddress()));
  }
}
