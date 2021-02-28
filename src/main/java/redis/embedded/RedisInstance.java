package redis.embedded;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static redis.embedded.util.IO.*;

abstract class RedisInstance implements Redis {

    private final Pattern readyPattern;
    private final int port;
    private final List<String> args;

    private volatile boolean active = false;
    private Process process;

    protected RedisInstance(final int port, final List<String> args, final Pattern readyPattern) {
        this.port = port;
        this.args = args;
        this.readyPattern = readyPattern;
    }

    public synchronized void start() throws IOException {
        if (active) return;

        try {
            process = new ProcessBuilder(args)
                .directory(new File(args.get(0)).getParentFile())
                .start();
            addShutdownHook("RedisInstanceCleaner", checkedToRuntime(this::stop));
            logStream(process.getErrorStream(), System.out::println);
            awaitServerReady();

            active = true;
        } catch (IOException e) {
            throw new IOException("Failed to start Redis instance", e);
        }
    }

    private void awaitServerReady() throws IOException {
        final StringBuilder log = new StringBuilder();
        if (!findMatchInStream(process.getInputStream(), readyPattern, log))
            throw new IOException("Can't start redis server. Check logs for details. Redis process log: " + log.toString());
    }

    public synchronized void stop() throws IOException {
        if (!active) return;

        try {
            process.destroy();
            process.waitFor();
            active = false;
        } catch (InterruptedException e) {
            throw new IOException("Failed to stop redis instance", e);
        }
    }

    public boolean isActive() {
        return active;
    }

    public List<Integer> ports() {
        return Arrays.asList(port);
    }

}
