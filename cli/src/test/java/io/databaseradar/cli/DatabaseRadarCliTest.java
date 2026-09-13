package io.databaseradar.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseRadarCliTest {
    @Test
    void rootHelpNamesCoreCommands() {
        StringWriter output = new StringWriter();
        CommandLine cli = new CommandLine(new DatabaseRadarCli());
        cli.setOut(new PrintWriter(output));

        int exit = cli.execute("--help");

        assertEquals(0, exit);
        assertTrue(output.toString().contains("scan"));
        assertTrue(output.toString().contains("writers"));
        assertTrue(output.toString().contains("impact"));
    }
}
