package views;

import graph.Graph;
import graph.Message;
import graph.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class HtmlGraphWriter {
    private HtmlGraphWriter() {
    }

    public static List<String> getGraphHTML(Graph graph) {
        String svg = buildSvg(graph);
        String template = readTemplate();
        return List.of(template.replace("{{GRAPH}}", svg));
    }

    private static String readTemplate() {
        Path template = Path.of("html_files", "graph.html");
        if (Files.exists(template)) {
            try {
                return Files.readString(template);
            } catch (IOException e) {
                // Fall back to generated HTML below.
            }
        }
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Graph</title></head><body>{{GRAPH}}</body></html>";
    }

    private static String buildSvg(Graph graph) {
        if (graph == null || graph.isEmpty()) {
            return "<p>No graph loaded.</p>";
        }

        // Acyclic graphs read best as left-to-right pipelines; cycles need a
        // circular layout so feedback edges do not fold back over every node.
        Layout layout = graph.hasCycles() ? circularLayout(graph) : rankedLayout(graph);
        Map<Node, NodeBox> boxes = nodeBoxes(graph);
        StringBuilder svg = new StringBuilder();
        svg.append("<svg viewBox=\"0 0 ").append(layout.width).append(" ").append(layout.height)
                .append("\" role=\"img\">");
        svg.append("<defs>");
        svg.append("<marker id=\"arrow\" viewBox=\"0 0 10 10\" refX=\"9\" refY=\"5\" markerWidth=\"7\" markerHeight=\"7\" orient=\"auto-start-reverse\">");
        svg.append("<path d=\"M 0 0 L 10 5 L 0 10 z\" fill=\"#334e68\"></path></marker>");
        svg.append("<filter id=\"shadow\" x=\"-20%\" y=\"-20%\" width=\"140%\" height=\"140%\">");
        svg.append("<feDropShadow dx=\"0\" dy=\"4\" stdDeviation=\"4\" flood-color=\"#102a43\" flood-opacity=\"0.18\"></feDropShadow>");
        svg.append("</filter></defs>");
        svg.append("<text class=\"caption\" x=\"24\" y=\"28\">")
                .append(layout.cyclic ? "cycle-aware circular layout" : "inputs left, outputs right")
                .append("</text>");
        svg.append("<text class=\"caption\" x=\"24\" y=\"46\">")
                .append(graph.size()).append(" nodes, ").append(countEdges(graph)).append(" edges")
                .append("</text>");

        List<EdgeInfo> edges = collectEdges(graph, layout);
        Map<Node, Integer> labelGroupCounts = new HashMap<>();
        Map<Node, Integer> labelGroupIndexes = new HashMap<>();
        Map<String, Integer> routeGroupCounts = new HashMap<>();
        Map<String, Integer> routeGroupIndexes = new HashMap<>();
        for (EdgeInfo edge : edges) {
            if (!edge.value.isEmpty()) {
                Node group = labelGroup(edge);
                labelGroupCounts.put(group, labelGroupCounts.getOrDefault(group, 0) + 1);
            }
            String routeGroup = routeGroup(edge);
            routeGroupCounts.put(routeGroup, routeGroupCounts.getOrDefault(routeGroup, 0) + 1);
        }

        for (Node node : graph) {
            Point point = layout.positions.get(node);
            String label = displayLabel(node);
            NodeBox box = boxes.get(node);
            if (isTopic(node)) {
                svg.append("<rect class=\"topic\" filter=\"url(#shadow)\" x=\"").append(point.x - box.width / 2).append("\" y=\"")
                        .append(point.y - box.height / 2).append("\" width=\"").append(box.width)
                        .append("\" height=\"").append(box.height).append("\" rx=\"5\"></rect>");
            } else if (isBooleanGate(label)) {
                appendGate(svg, label, point, box);
            } else {
                svg.append("<ellipse class=\"agent\" filter=\"url(#shadow)\" cx=\"").append(point.x).append("\" cy=\"")
                        .append(point.y).append("\" rx=\"").append(box.width / 2)
                        .append("\" ry=\"").append(box.height / 2).append("\"></ellipse>");
            }
        }

        for (EdgeInfo edge : edges) {
            Node group = labelGroup(edge);
            int labelCount = labelGroupCounts.getOrDefault(group, 1);
            int labelIndex = labelGroupIndexes.getOrDefault(group, 0);
            if (!edge.value.isEmpty()) {
                labelGroupIndexes.put(group, labelIndex + 1);
            }
            String routeGroup = routeGroup(edge);
            int routeCount = routeGroupCounts.getOrDefault(routeGroup, 1);
            int routeIndex = routeGroupIndexes.getOrDefault(routeGroup, 0);
            routeGroupIndexes.put(routeGroup, routeIndex + 1);
            appendEdge(svg, edge, layout.positions, boxes, layout.cyclic, labelIndex, labelCount, routeIndex,
                    routeCount);
        }

        for (Node node : graph) {
            Point point = layout.positions.get(node);
            String label = displayLabel(node);
            if (isBooleanGate(label)) {
                NodeBox box = boxes.get(node);
                svg.append("<text class=\"node-label gate-label\" x=\"").append(point.x).append("\" y=\"")
                        .append(point.y).append("\">").append(escape(gateType(label))).append("</text>");
                svg.append("<text class=\"node-sublabel\" x=\"").append(point.x).append("\" y=\"")
                        .append(point.y + box.height / 2 + 18).append("\">").append(escape(label))
                        .append("</text>");
            } else {
                svg.append("<text class=\"node-label\" x=\"").append(point.x).append("\" y=\"")
                        .append(point.y).append("\">").append(escape(label)).append("</text>");
            }
        }

        svg.append("</svg>");
        return svg.toString();
    }

    private static Layout rankedLayout(Graph graph) {
        List<Node> activeNodes = connectedNodes(graph);
        List<Node> isolatedNodes = isolatedNodes(graph);
        if (activeNodes.isEmpty()) {
            activeNodes = sortedNodes(graph);
            isolatedNodes = new ArrayList<>();
        }

        Map<Node, Integer> indegree = new HashMap<>();
        Map<Node, Integer> level = new HashMap<>();
        for (Node node : activeNodes) {
            indegree.put(node, 0);
            level.put(node, 0);
        }
        for (Node node : activeNodes) {
            for (Node edge : node.getEdges()) {
                if (indegree.containsKey(edge)) {
                    indegree.put(edge, indegree.get(edge) + 1);
                }
            }
        }

        Queue<Node> queue = new ArrayDeque<>();
        for (Node node : sortedNodeList(activeNodes)) {
            if (indegree.get(node) == 0) {
                queue.add(node);
            }
        }

        while (!queue.isEmpty()) {
            Node node = queue.remove();
            for (Node edge : node.getEdges()) {
                if (!indegree.containsKey(edge)) {
                    continue;
                }
                level.put(edge, Math.max(level.get(edge), level.get(node) + 1));
                indegree.put(edge, indegree.get(edge) - 1);
                if (indegree.get(edge) == 0) {
                    queue.add(edge);
                }
            }
        }

        Map<Integer, List<Node>> columns = new LinkedHashMap<>();
        int maxLevel = 0;
        for (Node node : sortedNodeList(activeNodes)) {
            int nodeLevel = level.get(node);
            maxLevel = Math.max(maxLevel, nodeLevel);
            columns.computeIfAbsent(nodeLevel, key -> new ArrayList<>()).add(node);
        }

        int columnGap = 250;
        int rowGap = 92;
        int marginX = 120;
        int marginY = 82;
        int width = Math.max(760, marginX * 2 + maxLevel * columnGap);
        if (!isolatedNodes.isEmpty()) {
            width = Math.max(width, marginX * 2 + Math.min(isolatedNodes.size(), 6) * 112);
        }
        int largestColumn = 1;
        for (List<Node> column : columns.values()) {
            largestColumn = Math.max(largestColumn, column.size());
        }
        int mainHeight = Math.max(560, marginY * 2 + (largestColumn - 1) * rowGap);
        int isolatedRows = isolatedNodes.isEmpty() ? 0 : (isolatedNodes.size() + 5) / 6;
        int height = mainHeight + isolatedRows * 86;

        orderColumnsByFlow(columns, maxLevel);

        Map<Node, Point> positions = new HashMap<>();
        for (Map.Entry<Integer, List<Node>> entry : columns.entrySet()) {
            List<Node> column = entry.getValue();
            int x = marginX + entry.getKey() * columnGap;
            int startY = mainHeight / 2 - (column.size() - 1) * rowGap / 2;
            for (int i = 0; i < column.size(); i++) {
                positions.put(column.get(i), new Point(x, startY + i * rowGap));
            }
        }
        for (int i = 0; i < isolatedNodes.size(); i++) {
            int row = i / 6;
            int column = i % 6;
            int x = marginX + column * 112;
            int y = mainHeight + 44 + row * 86;
            positions.put(isolatedNodes.get(i), new Point(x, y));
        }

        return new Layout(positions, width, height, false);
    }

    private static void orderColumnsByFlow(Map<Integer, List<Node>> columns, int maxLevel) {
        Map<Node, Integer> order = new HashMap<>();
        for (int level = 0; level <= maxLevel; level++) {
            List<Node> column = columns.get(level);
            if (column == null) {
                continue;
            }
            if (level == 0) {
                column.sort(Comparator.comparing(HtmlGraphWriter::displayLabel));
            } else {
                column.sort(Comparator.comparingDouble((Node node) -> averageIncomingOrder(node, order))
                        .thenComparing(HtmlGraphWriter::displayLabel));
            }
            for (int i = 0; i < column.size(); i++) {
                order.put(column.get(i), i);
            }
        }
    }

    private static double averageIncomingOrder(Node node, Map<Node, Integer> order) {
        int total = 0;
        int count = 0;
        for (Map.Entry<Node, Integer> entry : order.entrySet()) {
            if (entry.getKey().getEdges().contains(node)) {
                total += entry.getValue();
                count++;
            }
        }
        return count == 0 ? 0 : (double) total / count;
    }

    private static Layout circularLayout(Graph graph) {
        List<Node> nodes = connectedCycleOrder(graph);
        int count = nodes.size();
        int width = Math.max(760, 220 + count * 55);
        int height = Math.max(620, 220 + count * 45);
        int centerX = width / 2;
        int centerY = height / 2;
        int radiusX = Math.max(230, width / 2 - 150);
        int radiusY = Math.max(190, height / 2 - 130);

        Map<Node, Point> positions = new HashMap<>();
        for (int i = 0; i < count; i++) {
            double angle = -Math.PI / 2 + (Math.PI * 2 * i / count);
            int x = centerX + (int) Math.round(Math.cos(angle) * radiusX);
            int y = centerY + (int) Math.round(Math.sin(angle) * radiusY);
            positions.put(nodes.get(i), new Point(x, y));
        }
        return new Layout(positions, width, height, true);
    }

    private static List<Node> connectedCycleOrder(Graph graph) {
        List<Node> ordered = new ArrayList<>();
        Set<Node> visited = new HashSet<>();
        Node current = highestDegreeNode(graph, visited);

        while (ordered.size() < graph.size()) {
            if (current == null || visited.contains(current)) {
                current = highestDegreeNode(graph, visited);
            }
            if (current == null) {
                break;
            }

            ordered.add(current);
            visited.add(current);
            current = nextConnectedNode(graph, current, visited);
        }

        return ordered;
    }

    private static Node nextConnectedNode(Graph graph, Node current, Set<Node> visited) {
        List<Node> candidates = new ArrayList<>();
        for (Node edge : current.getEdges()) {
            if (!visited.contains(edge)) {
                candidates.add(edge);
            }
        }
        for (Node node : graph) {
            if (!visited.contains(node) && node.getEdges().contains(current)) {
                candidates.add(node);
            }
        }
        candidates.sort(Comparator.comparingInt((Node node) -> degree(graph, node)).reversed()
                .thenComparing(HtmlGraphWriter::displayLabel));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static Node highestDegreeNode(Graph graph, Set<Node> visited) {
        return sortedNodes(graph).stream()
                .filter(node -> !visited.contains(node))
                .max(Comparator.comparingInt((Node node) -> degree(graph, node))
                        .thenComparing(HtmlGraphWriter::displayLabel))
                .orElse(null);
    }

    private static int degree(Graph graph, Node node) {
        int degree = node.getEdges().size();
        for (Node other : graph) {
            if (other != node && other.getEdges().contains(node)) {
                degree++;
            }
        }
        return degree;
    }

    private static List<Node> sortedNodes(Graph graph) {
        return sortedNodeList(new ArrayList<>(graph));
    }

    private static List<Node> sortedNodeList(List<Node> source) {
        List<Node> nodes = new ArrayList<>(source);
        nodes.sort(Comparator.comparing(HtmlGraphWriter::displayLabel)
                .thenComparing(node -> node.getName() == null ? "" : node.getName()));
        return nodes;
    }

    private static List<Node> connectedNodes(Graph graph) {
        List<Node> result = new ArrayList<>();
        for (Node node : graph) {
            if (degree(graph, node) > 0) {
                result.add(node);
            }
        }
        return sortedNodeList(result);
    }

    private static List<Node> isolatedNodes(Graph graph) {
        List<Node> result = new ArrayList<>();
        for (Node node : graph) {
            if (degree(graph, node) == 0) {
                result.add(node);
            }
        }
        return sortedNodeList(result);
    }

    private static List<EdgeInfo> collectEdges(Graph graph, Layout layout) {
        List<EdgeInfo> edges = new ArrayList<>();
        for (Node from : graph) {
            Point start = layout.positions.get(from);
            if (start == null) {
                continue;
            }
            for (Node to : from.getEdges()) {
                Point end = layout.positions.get(to);
                if (end != null) {
                    edges.add(new EdgeInfo(from, to, start, end, edgeValue(from, to)));
                }
            }
        }
        edges.sort(Comparator.comparing((EdgeInfo edge) -> displayLabel(edge.from))
                .thenComparing(edge -> displayLabel(edge.to)));
        return edges;
    }

    private static Node labelGroup(EdgeInfo edge) {
        return isTopic(edge.to) ? edge.from : edge.to;
    }

    private static String routeGroup(EdgeInfo edge) {
        if (!isTopic(edge.to)) {
            return "in:" + edge.to.getName();
        }
        if (!isTopic(edge.from)) {
            return "out:" + edge.from.getName();
        }
        int startColumn = edge.start.x / 180;
        int endColumn = edge.end.x / 180;
        int direction = Integer.compare(edge.end.x, edge.start.x);
        return startColumn + ":" + endColumn + ":" + direction;
    }

    private static Map<Node, NodeBox> nodeBoxes(Graph graph) {
        Map<Node, NodeBox> boxes = new HashMap<>();
        for (Node node : graph) {
            String label = displayLabel(node);
            if (isTopic(node)) {
                int width = Math.max(86, label.length() * 8 + 32);
                boxes.put(node, new NodeBox(width, 52));
            } else if (isBooleanGate(label)) {
                int width = Math.max(148, Math.min(230, label.length() * 6 + 42));
                boxes.put(node, new NodeBox(width, 72));
            } else {
                int width = Math.max(118, label.length() * 8 + 42);
                boxes.put(node, new NodeBox(width, 58));
            }
        }
        return boxes;
    }

    private static void appendGate(StringBuilder svg, String label, Point point, NodeBox box) {
        String type = gateType(label);
        int left = point.x - box.width / 2;
        int right = point.x + box.width / 2;
        int top = point.y - box.height / 2;
        int bottom = point.y + box.height / 2;
        int midY = point.y;
        int bubbleRadius = 6;
        int bubbleShift = hasInversionBubble(type) ? bubbleRadius * 2 : 0;
        int bodyRight = right - bubbleShift;
        int gateLeft = left + ("XOR".equals(type) || "XNOR".equals(type) ? 12 : 0);

        if ("AND".equals(type) || "NAND".equals(type)) {
            int flatEnd = left + Math.max(44, (bodyRight - left) / 2);
            svg.append("<path class=\"agent gate\" filter=\"url(#shadow)\" d=\"M ").append(left).append(" ").append(top)
                    .append(" L ").append(flatEnd).append(" ").append(top)
                    .append(" C ").append(bodyRight + 4).append(" ").append(top)
                    .append(" ").append(bodyRight + 4).append(" ").append(bottom)
                    .append(" ").append(flatEnd).append(" ").append(bottom)
                    .append(" L ").append(left).append(" ").append(bottom)
                    .append(" Z\"></path>");
        } else if ("BUF".equals(type) || "NOT".equals(type)) {
            svg.append("<path class=\"agent gate\" filter=\"url(#shadow)\" d=\"M ").append(left).append(" ").append(top)
                    .append(" L ").append(bodyRight).append(" ").append(midY)
                    .append(" L ").append(left).append(" ").append(bottom).append(" Z\"></path>");
        } else if ("MAJORITY".equals(type)) {
            int shoulder = left + 22;
            svg.append("<path class=\"agent gate\" filter=\"url(#shadow)\" d=\"M ").append(left).append(" ").append(top)
                    .append(" L ").append(bodyRight - 14).append(" ").append(top + 7)
                    .append(" Q ").append(bodyRight).append(" ").append(midY).append(" ").append(bodyRight - 14)
                    .append(" ").append(bottom - 7)
                    .append(" L ").append(left).append(" ").append(bottom)
                    .append(" Q ").append(shoulder).append(" ").append(midY).append(" ").append(left)
                    .append(" ").append(top).append(" Z\"></path>");
        } else {
            int waist = gateLeft + Math.max(30, (bodyRight - gateLeft) / 4);
            int nose = bodyRight;
            svg.append("<path class=\"agent gate\" filter=\"url(#shadow)\" d=\"M ").append(gateLeft).append(" ")
                    .append(top)
                    .append(" C ").append(waist).append(" ").append(top + 3)
                    .append(" ").append(nose - 30).append(" ").append(top + 6)
                    .append(" ").append(nose).append(" ").append(midY)
                    .append(" C ").append(nose - 30).append(" ").append(bottom - 6)
                    .append(" ").append(waist).append(" ").append(bottom - 3)
                    .append(" ").append(gateLeft).append(" ").append(bottom)
                    .append(" C ").append(gateLeft + 22).append(" ").append(midY)
                    .append(" ").append(gateLeft + 22).append(" ").append(midY)
                    .append(" ").append(gateLeft).append(" ").append(top)
                    .append(" Z\"></path>");
            if ("XOR".equals(type) || "XNOR".equals(type)) {
                svg.append("<path class=\"gate-extra\" d=\"M ").append(left).append(" ").append(top)
                        .append(" C ").append(left + 22).append(" ").append(midY)
                        .append(" ").append(left + 22).append(" ").append(midY)
                        .append(" ").append(left).append(" ").append(bottom).append("\"></path>");
            }
        }

        if (hasInversionBubble(type)) {
            svg.append("<circle class=\"gate-bubble\" cx=\"").append(right - bubbleRadius).append("\" cy=\"")
                    .append(midY).append("\" r=\"").append(bubbleRadius).append("\"></circle>");
        }
    }

    private static boolean isBooleanGate(String label) {
        String type = gateType(label);
        return type.equals("AND") || type.equals("OR") || type.equals("XOR") || type.equals("NAND")
                || type.equals("NOR") || type.equals("XNOR") || type.equals("MAJORITY")
                || type.equals("NOT") || type.equals("BUF");
    }

    private static String gateType(String label) {
        if (label == null) {
            return "";
        }
        int paren = label.indexOf('(');
        String type = paren >= 0 ? label.substring(0, paren) : label;
        return type.trim().toUpperCase();
    }

    private static boolean hasInversionBubble(String type) {
        return type.equals("NAND") || type.equals("NOR") || type.equals("XNOR") || type.equals("NOT");
    }

    private static void appendEdge(StringBuilder svg, EdgeInfo edge, Map<Node, Point> positions,
            Map<Node, NodeBox> boxes, boolean cyclic, int labelIndex, int labelCount, int routeIndex,
            int routeCount) {
        Point visibleStart = boundaryPoint(edge.start, edge.end, boxes.get(edge.from));
        Point visibleEnd = boundaryPoint(edge.end, edge.start, boxes.get(edge.to));
        if (!cyclic) {
            visibleStart = routedPort(edge.from, edge.to, edge.start, visibleStart, boxes.get(edge.from),
                    routeIndex, routeCount, true);
            visibleEnd = routedPort(edge.to, edge.from, edge.end, visibleEnd, boxes.get(edge.to),
                    routeIndex, routeCount, false);
        }
        Route route = routeFor(edge, visibleStart, visibleEnd, positions, boxes, cyclic, routeIndex, routeCount);

        svg.append("<path class=\"edge");
        if (cyclic) {
            svg.append(" cycle");
        }
        if ("bus".equals(route.kind)) {
            svg.append(" routed");
        }
        svg.append("\" d=\"").append(route.path).append("\"></path>");

        if (!edge.value.isEmpty()) {
            Point labelPoint = labelPoint(route, visibleStart, visibleEnd, labelIndex, labelCount);
            int labelWidth = Math.max(34, edge.value.length() * 8 + 18);
            svg.append("<rect class=\"edge-label-bg\" x=\"").append(labelPoint.x - labelWidth / 2).append("\" y=\"")
                    .append(labelPoint.y - 12).append("\" width=\"").append(labelWidth)
                    .append("\" height=\"22\" rx=\"11\"></rect>");
            svg.append("<text class=\"edge-label\" x=\"").append(labelPoint.x).append("\" y=\"")
                    .append(labelPoint.y).append("\">").append(escape(edge.value)).append("</text>");
        }
    }

    private static Route routeFor(EdgeInfo edge, Point visibleStart, Point visibleEnd, Map<Node, Point> positions,
            Map<Node, NodeBox> boxes, boolean cyclic, int laneIndex, int laneCount) {
        int dx = visibleEnd.x - visibleStart.x;
        int dy = visibleEnd.y - visibleStart.y;
        if (cyclic) {
            double length = Math.sqrt((double) dx * dx + (double) dy * dy);
            if (length < 260 && !crossesAnyNode(visibleStart, visibleEnd, edge, positions, boxes)) {
                String path = "M " + visibleStart.x + " " + visibleStart.y + " L " + visibleEnd.x + " "
                        + visibleEnd.y;
                return new Route(path, "line", 0);
            }

            int midX = visibleStart.x + dx / 2;
            int midY = visibleStart.y + dy / 2;
            Point center = centerOf(positions);
            double normalX = length == 0 ? 0 : -dy / length;
            double normalY = length == 0 ? -1 : dx / length;
            double outwardA = squaredDistance(midX + normalX * 100, midY + normalY * 100, center);
            double outwardB = squaredDistance(midX - normalX * 100, midY - normalY * 100, center);
            if (outwardB > outwardA) {
                normalX = -normalX;
                normalY = -normalY;
            }

            double tangentX = length == 0 ? 1 : dx / length;
            double tangentY = length == 0 ? 0 : dy / length;
            double laneMid = (laneCount - 1) / 2.0;
            int bend = (int) Math.max(62, Math.min(170, length / 3 + laneIndex * 34));
            int laneShift = (int) Math.round((laneIndex - laneMid) * 54);
            int controlX = midX + (int) Math.round(normalX * bend + tangentX * laneShift);
            int controlY = midY + (int) Math.round(normalY * bend + tangentY * laneShift);
            String path = "M " + visibleStart.x + " " + visibleStart.y
                    + " Q " + controlX + " " + controlY + " " + visibleEnd.x + " " + visibleEnd.y;
            return new Route(path, "quad", controlX, controlY);
        }
        if (!cyclic && Math.abs(dy) <= 14 && !crossesAnyNode(visibleStart, visibleEnd, edge, positions, boxes)) {
            String path = "M " + visibleStart.x + " " + visibleStart.y + " L " + visibleEnd.x + " " + visibleEnd.y;
            return new Route(path, "line", 0);
        }

        if (!cyclic && !crossesAnyNode(visibleStart, visibleEnd, edge, positions, boxes) && Math.abs(dx) <= 320) {
            double laneMid = (laneCount - 1) / 2.0;
            int laneOffset = (int) Math.round((laneIndex - laneMid) * 38);
            int controlX = visibleStart.x + dx / 2;
            int controlY = visibleStart.y + dy / 2 - Math.min(58, Math.abs(dx) / 5) + laneOffset;
            String path = "M " + visibleStart.x + " " + visibleStart.y
                    + " Q " + controlX + " " + controlY + " " + visibleEnd.x + " " + visibleEnd.y;
            return new Route(path, "quad", controlX, controlY);
        }

        if (!cyclic && dx > 0) {
            double laneMid = (laneCount - 1) / 2.0;
            int laneOffset = (int) Math.round((laneIndex - laneMid) * 18);
            int control1X = visibleStart.x + Math.max(46, dx / 3);
            int control2X = visibleEnd.x - Math.max(46, dx / 3);
            int control1Y = visibleStart.y + laneOffset;
            int control2Y = visibleEnd.y - laneOffset;
            String path = "M " + visibleStart.x + " " + visibleStart.y
                    + " C " + control1X + " " + control1Y
                    + " " + control2X + " " + control2Y
                    + " " + visibleEnd.x + " " + visibleEnd.y;
            return new Route(path, "cubic", (control1X + control2X) / 2, (control1Y + control2Y) / 2);
        }

        double laneMid = (laneCount - 1) / 2.0;
        int laneOffset = (int) Math.round((laneIndex - laneMid) * 54);
        int routeY = chooseRouteY(visibleStart, visibleEnd, positions, boxes) + laneOffset;
        int midX = visibleStart.x + dx / 2 + laneOffset / 2;
        String path = "M " + visibleStart.x + " " + visibleStart.y
                + " L " + midX + " " + visibleStart.y
                + " L " + midX + " " + routeY
                + " L " + visibleEnd.x + " " + routeY
                + " L " + visibleEnd.x + " " + visibleEnd.y;
        return new Route(path, "bus", midX, routeY);
    }

    private static Point routedPort(Node node, Node other, Point center, Point fallback, NodeBox box,
            int laneIndex, int laneCount, boolean isStart) {
        if (box == null || other == null || laneCount <= 1) {
            return fallback;
        }

        int sideX;
        if (other == node) {
            return fallback;
        } else if (fallback.x >= center.x) {
            sideX = center.x + box.width / 2;
        } else {
            sideX = center.x - box.width / 2;
        }

        double mid = (laneCount - 1) / 2.0;
        int usable = Math.max(18, box.height - 18);
        int step = laneCount <= 1 ? 0 : Math.max(7, Math.min(18, usable / Math.max(1, laneCount - 1)));
        int y = center.y + (int) Math.round((laneIndex - mid) * step);
        int top = center.y - box.height / 2 + 8;
        int bottom = center.y + box.height / 2 - 8;
        y = Math.max(top, Math.min(bottom, y));

        return new Point(sideX, y);
    }

    private static Point centerOf(Map<Node, Point> positions) {
        if (positions.isEmpty()) {
            return new Point(0, 0);
        }
        int sumX = 0;
        int sumY = 0;
        for (Point point : positions.values()) {
            sumX += point.x;
            sumY += point.y;
        }
        return new Point(sumX / positions.size(), sumY / positions.size());
    }

    private static double squaredDistance(double x, double y, Point point) {
        double dx = x - point.x;
        double dy = y - point.y;
        return dx * dx + dy * dy;
    }

    private static Point labelPoint(Route route, Point start, Point end, int labelIndex, int labelCount) {
        double mid = (labelCount - 1) / 2.0;
        int offset = (int) Math.round((labelIndex - mid) * 18);
        if ("line".equals(route.kind)) {
            return new Point((start.x + end.x) / 2, (start.y + end.y) / 2 - 14 + offset);
        }
        if ("bus".equals(route.kind)) {
            return new Point((route.controlX + end.x) / 2, route.controlY - 12 + offset);
        }

        double t = clamp(0.5 + (labelIndex - mid) * 0.16, 0.22, 0.78);
        int curveX = (int) Math.round(quadratic(start.x, route.controlX, end.x, t));
        int curveY = (int) Math.round(quadratic(start.y, route.controlY, end.y, t));
        return new Point(curveX, curveY - 8 + offset);
    }

    private static boolean crossesAnyNode(Point start, Point end, EdgeInfo edge, Map<Node, Point> positions,
            Map<Node, NodeBox> boxes) {
        for (Map.Entry<Node, Point> entry : positions.entrySet()) {
            Node node = entry.getKey();
            if (node == edge.from || node == edge.to) {
                continue;
            }
            if (segmentIntersectsBox(start, end, entry.getValue(), boxes.get(node), 14)) {
                return true;
            }
        }
        return false;
    }

    private static int countEdges(Graph graph) {
        int count = 0;
        for (Node node : graph) {
            count += node.getEdges().size();
        }
        return count;
    }

    private static boolean segmentIntersectsBox(Point start, Point end, Point center, NodeBox box, int padding) {
        if (box == null) {
            return false;
        }
        int left = center.x - box.width / 2 - padding;
        int right = center.x + box.width / 2 + padding;
        int top = center.y - box.height / 2 - padding;
        int bottom = center.y + box.height / 2 + padding;

        if (pointInBox(start, left, right, top, bottom) || pointInBox(end, left, right, top, bottom)) {
            return true;
        }
        return segmentsIntersect(start, end, new Point(left, top), new Point(right, top))
                || segmentsIntersect(start, end, new Point(right, top), new Point(right, bottom))
                || segmentsIntersect(start, end, new Point(right, bottom), new Point(left, bottom))
                || segmentsIntersect(start, end, new Point(left, bottom), new Point(left, top));
    }

    private static boolean pointInBox(Point point, int left, int right, int top, int bottom) {
        return point.x >= left && point.x <= right && point.y >= top && point.y <= bottom;
    }

    private static boolean segmentsIntersect(Point a, Point b, Point c, Point d) {
        int d1 = direction(c, d, a);
        int d2 = direction(c, d, b);
        int d3 = direction(a, b, c);
        int d4 = direction(a, b, d);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
                && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private static int direction(Point a, Point b, Point c) {
        long value = (long) (c.x - a.x) * (b.y - a.y) - (long) (c.y - a.y) * (b.x - a.x);
        return Long.compare(value, 0);
    }

    private static int chooseRouteY(Point start, Point end, Map<Node, Point> positions, Map<Node, NodeBox> boxes) {
        int top = Math.min(start.y, end.y);
        int bottom = Math.max(start.y, end.y);
        for (Map.Entry<Node, Point> entry : positions.entrySet()) {
            NodeBox box = boxes.get(entry.getKey());
            if (box == null) {
                continue;
            }
            top = Math.min(top, entry.getValue().y - box.height / 2);
            bottom = Math.max(bottom, entry.getValue().y + box.height / 2);
        }

        int above = Math.max(54, top - 46);
        int below = bottom + 46;
        int averageY = (start.y + end.y) / 2;
        return Math.abs(averageY - above) <= Math.abs(averageY - below) ? above : below;
    }

    private static double quadratic(double start, double control, double end, double t) {
        double oneMinusT = 1 - t;
        return oneMinusT * oneMinusT * start + 2 * oneMinusT * t * control + t * t * end;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Point boundaryPoint(Point center, Point toward, NodeBox box) {
        double dx = toward.x - center.x;
        double dy = toward.y - center.y;
        if (box == null || (dx == 0 && dy == 0)) {
            return center;
        }
        double halfWidth = box.width / 2.0;
        double halfHeight = box.height / 2.0;
        double scale = Math.min(Math.abs(halfWidth / dx), Math.abs(halfHeight / dy));
        if (Double.isInfinite(scale)) {
            scale = Math.abs(dx) < 0.01 ? Math.abs(halfHeight / dy) : Math.abs(halfWidth / dx);
        }
        int x = center.x + (int) Math.round(dx * scale);
        int y = center.y + (int) Math.round(dy * scale);
        return new Point(x, y);
    }

    private static String edgeValue(Node from, Node to) {
        Message message = null;
        if (isTopic(from)) {
            message = from.getMessage();
        } else if (isTopic(to)) {
            message = to.getMessage();
        }
        if (message == null || message.asText == null || message.asText.isEmpty()) {
            return "";
        }
        return message.asText;
    }

    private static boolean isTopic(Node node) {
        return node.getName() != null && node.getName().startsWith("T");
    }

    private static String displayLabel(Node node) {
        String name = node.getName();
        if (name == null || name.length() <= 1) {
            return "";
        }
        if (isTopic(node) || name.startsWith("A")) {
            return name.substring(1);
        }
        return name;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static class Layout {
        private final Map<Node, Point> positions;
        private final int width;
        private final int height;
        private final boolean cyclic;

        private Layout(Map<Node, Point> positions, int width, int height, boolean cyclic) {
            this.positions = positions;
            this.width = width;
            this.height = height;
            this.cyclic = cyclic;
        }
    }

    private static class NodeBox {
        private final int width;
        private final int height;

        private NodeBox(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private static class EdgeInfo {
        private final Node from;
        private final Node to;
        private final Point start;
        private final Point end;
        private final String value;

        private EdgeInfo(Node from, Node to, Point start, Point end, String value) {
            this.from = from;
            this.to = to;
            this.start = start;
            this.end = end;
            this.value = value;
        }
    }

    private static class Route {
        private final String path;
        private final String kind;
        private final int controlX;
        private final int controlY;

        private Route(String path, String kind, int controlX, int controlY) {
            this.path = path;
            this.kind = kind;
            this.controlX = controlX;
            this.controlY = controlY;
        }

        private Route(String path, String kind, int controlX) {
            this(path, kind, controlX, 0);
        }
    }

    private static class Point {
        private final int x;
        private final int y;

        private Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
