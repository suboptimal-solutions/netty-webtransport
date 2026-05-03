package io.suboptimal.netty.webtransport.tests;

/**
 * Test-side record of an event observed by the server fixture. The {@link
 * InteropEchoServer} pushes these into a {@link java.util.concurrent.BlockingQueue} so JUnit
 * tests can assert that the server saw what the browser sent.
 */
sealed interface Observation {

    record SessionEstablished(long sessionId) implements Observation {}

    record SessionDraining(long sessionId) implements Observation {}

    record SessionClosed(long sessionId, int errorCode, String errorMessage)
            implements Observation {}

    record BidiStreamOpened(long sessionId) implements Observation {}

    record UniStreamOpened(long sessionId) implements Observation {}

    record StreamPayload(long sessionId, String text) implements Observation {}

    record DatagramReceived(long sessionId, String text) implements Observation {}
}
