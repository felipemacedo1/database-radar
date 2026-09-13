package io.databaseradar.spike;

import java.nio.file.Path;

public final class SpikeMain {
    private SpikeMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: SpikeMain <Java source file>");
            System.exit(2);
        }
        SpikeFinding finding = new SpikeAnalyzer().analyze(Path.of(args[0]));
        System.out.printf("%s:%d%n", finding.file(), finding.line());
        System.out.printf(" -> method PedidoDAO.%s()%n", finding.method());
        System.out.printf(" -> %s %s%n", finding.edge(), finding.table());
        System.out.printf(" -> evidence %s%n", finding.confidence());
    }
}
