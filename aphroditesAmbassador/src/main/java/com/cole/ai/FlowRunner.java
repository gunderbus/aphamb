package com.cole.ai;

import com.cole.objects.node;
import com.cole.objects.node.NodeKind;
import com.cole.objects.node.OutputLink;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FlowRunner {
    private final OllamaClient client;

    public FlowRunner(OllamaClient client) {
        this.client = client;
    }

    public RunResult runFlow(
        String baseUrl,
        String model,
        String userMessage,
        String transcript,
        List<node> nodes,
        List<FlowConnection> connections
    ) throws IOException, InterruptedException {
        var startNode = findFirstNodeByKind(nodes, NodeKind.START);
        if (startNode == null) {
            throw new IOException("No Start node exists in the flowchart.");
        }

        var connectionMap = buildConnectionMap(connections);
        var assistantBuilder = new StringBuilder();
        var traversalBuilder = new StringBuilder();

        var currentNode = startNode;
        int safetyCounter = 0;

        while (currentNode != null && safetyCounter < 20) {
            safetyCounter++;
            traversalBuilder.append(currentNode.getNodeName())
                .append(" [")
                .append(currentNode.getNodeKind().getDisplayName())
                .append("]")
                .append('\n');

            if (currentNode.getNodeKind() == NodeKind.START
                || currentNode.getNodeKind() == NodeKind.END
                || currentNode.getNodeKind() == NodeKind.END_CONVERSATION) {
                var generated = generateAssistantText(baseUrl, model, currentNode, userMessage, transcript, assistantBuilder.toString());
                if (!generated.isBlank()) {
                    if (assistantBuilder.length() > 0) {
                        assistantBuilder.append("\n\n");
                    }
                    assistantBuilder.append(generated);
                }
            }

            if (currentNode.getNodeKind() == NodeKind.END || currentNode.getNodeKind() == NodeKind.END_CONVERSATION) {
                return new RunResult(assistantBuilder.toString().trim(), traversalBuilder.toString().trim());
            }

            var outputs = currentNode.getOutputs();
            if (outputs.isEmpty()) {
                throw new IOException("Node '" + currentNode.getNodeName() + "' has no outputs.");
            }

            int outputIndex;
            if (currentNode.getNodeKind() == NodeKind.START) {
                outputIndex = 0;
            } else {
                outputIndex = chooseBranch(baseUrl, model, currentNode, userMessage, transcript, assistantBuilder.toString());
            }

            if (outputIndex < 0 || outputIndex >= outputs.size()) {
                throw new IOException("The model chose an invalid branch on node '" + currentNode.getNodeName() + "'.");
            }

            var output = outputs.get(outputIndex);
            traversalBuilder.append(" -> ")
                .append(output.getLabel())
                .append('\n');

            var sourceNode = currentNode;
            currentNode = connectionMap.get(connectionKey(sourceNode, outputIndex));
            if (currentNode == null) {
                throw new IOException(
                    "Branch '" + output.getLabel() + "' on node '" + sourceNode.getNodeName()
                        + "' is not connected to another node."
                );
            }
        }

        throw new IOException("Flow execution stopped because it exceeded the safety limit of 20 steps.");
    }

    private Map<String, node> buildConnectionMap(List<FlowConnection> connections) {
        var map = new HashMap<String, node>();
        for (FlowConnection connection : connections) {
            map.put(connectionKey(connection.source(), connection.outputIndex()), connection.target());
        }
        return map;
    }

    private int chooseBranch(
        String baseUrl,
        String model,
        node decisionNode,
        String userMessage,
        String transcript,
        String generatedAssistantText
    ) throws IOException, InterruptedException {
        var outputs = decisionNode.getOutputs();
        var labels = new StringBuilder();
        for (int i = 0; i < outputs.size(); i++) {
            if (i > 0) {
                labels.append(", ");
            }
            labels.append(outputs.get(i).getLabel());
        }

        var instructions =
            "You are a strict flowchart router for a local conversation engine.";

        var input =
            instructions + "\n\n"
                + "Choose exactly one branch label from the provided options and return only that label.\n"
                + "Node name: " + decisionNode.getNodeName() + "\n"
                + "Decision rule: " + decisionNode.getPrompt() + "\n"
                + "Available branches: " + labels + "\n"
                + "Conversation transcript so far:\n" + safeText(transcript) + "\n"
                + "Assistant text generated so far:\n" + safeText(generatedAssistantText) + "\n"
                + "Latest user message:\n" + userMessage;

        var response = client.generate(baseUrl, model, input).trim();
        return matchBranch(response, outputs);
    }

    private String generateAssistantText(
        String baseUrl,
        String model,
        node currentNode,
        String userMessage,
        String transcript,
        String generatedAssistantText
    ) throws IOException, InterruptedException {
        var prompt =
            "You are the assistant speaking to the user. Follow the flowchart node prompt exactly and respond naturally. "
                + "Keep the reply brief: one or two short sentences unless the node prompt explicitly asks for more.\n\n"
                + "Node name: " + currentNode.getNodeName() + "\n"
                + "Node type: " + currentNode.getNodeKind().getDisplayName() + "\n"
                + "Node prompt: " + currentNode.getPrompt() + "\n"
                + "Conversation transcript so far:\n" + safeText(transcript) + "\n"
                + "Assistant text generated so far:\n" + safeText(generatedAssistantText) + "\n"
                + "Latest user message:\n" + userMessage;

        return client.generate(baseUrl, model, prompt).trim();
    }

    private int matchBranch(String response, List<OutputLink> outputs) {
        var normalized = response.trim().toLowerCase(Locale.ROOT);

        for (int i = 0; i < outputs.size(); i++) {
            var label = outputs.get(i).getLabel().trim().toLowerCase(Locale.ROOT);
            if (label.equals(normalized)) {
                return i;
            }
        }

        for (int i = 0; i < outputs.size(); i++) {
            var label = outputs.get(i).getLabel().trim().toLowerCase(Locale.ROOT);
            if (!label.isEmpty() && normalized.contains(label)) {
                return i;
            }
        }

        if (outputs.size() == 2) {
            if (normalized.contains("true") || normalized.contains("yes")) {
                return 0;
            }
            if (normalized.contains("false") || normalized.contains("no")) {
                return 1;
            }
        }

        return -1;
    }

    private node findFirstNodeByKind(List<node> nodes, NodeKind kind) {
        for (node current : nodes) {
            if (current.getNodeKind() == kind) {
                return current;
            }
        }
        return null;
    }

    private String connectionKey(node source, int outputIndex) {
        return System.identityHashCode(source) + ":" + outputIndex;
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }

    public static final class FlowConnection {
        private final node source;
        private final int outputIndex;
        private final node target;

        public FlowConnection(node source, int outputIndex, node target) {
            this.source = source;
            this.outputIndex = outputIndex;
            this.target = target;
        }

        public node source() {
            return source;
        }

        public int outputIndex() {
            return outputIndex;
        }

        public node target() {
            return target;
        }
    }

    public static final class RunResult {
        private final String assistantMessage;
        private final String traversalPath;

        public RunResult(String assistantMessage, String traversalPath) {
            this.assistantMessage = assistantMessage;
            this.traversalPath = traversalPath;
        }

        public String assistantMessage() {
            return assistantMessage;
        }

        public String traversalPath() {
            return traversalPath;
        }
    }
}
