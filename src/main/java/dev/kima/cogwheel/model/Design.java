package dev.kima.cogwheel.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Top-level container. A whole factory plan: nodes + edges.
 *
 * <p>Immutable. All mutating operations return a fresh Design. The {@code EditorScreen} holds a
 * mutable reference and swaps Designs on each user edit — that pattern keeps undo/redo trivial
 * (push the previous Design onto a stack) without needing copy-on-write inside individual nodes.
 */
public record Design(
        String name,
        List<Node> nodes,
        List<Edge> edges
) {
    public Node findNode(UUID id) {
        for (Node n : nodes) {
            if (n.id().equals(id)) return n;
        }
        return null;
    }

    public Design withNodeMoved(UUID id, Vec2 newPosition) {
        List<Node> updated = new ArrayList<>(nodes.size());
        for (Node n : nodes) {
            updated.add(n.id().equals(id) ? n.withPosition(newPosition) : n);
        }
        return new Design(name, List.copyOf(updated), edges);
    }

    /**
     * Replace a node in-place (preserves its position in the list, but uses {@code replacement} as-is).
     * Callers are responsible for making sure {@code replacement.id().equals(id)} so edges keep working.
     */
    public Design withNodeReplaced(UUID id, Node replacement) {
        List<Node> updated = new ArrayList<>(nodes.size());
        for (Node n : nodes) {
            updated.add(n.id().equals(id) ? replacement : n);
        }
        return new Design(name, List.copyOf(updated), edges);
    }

    public Design withNodeAdded(Node node) {
        List<Node> updated = new ArrayList<>(nodes);
        updated.add(node);
        return new Design(name, List.copyOf(updated), edges);
    }

    public Design withNodeRemoved(UUID id) {
        List<Node> remainingNodes = new ArrayList<>(nodes.size());
        for (Node n : nodes) {
            if (!n.id().equals(id)) remainingNodes.add(n);
        }
        List<Edge> remainingEdges = new ArrayList<>(edges.size());
        for (Edge e : edges) {
            if (!e.from().equals(id) && !e.to().equals(id)) remainingEdges.add(e);
        }
        return new Design(name, List.copyOf(remainingNodes), List.copyOf(remainingEdges));
    }

    public Design withEdgeAdded(Edge edge) {
        List<Edge> updated = new ArrayList<>(edges);
        updated.add(edge);
        return new Design(name, nodes, List.copyOf(updated));
    }

    public Design withEdgeRemoved(Edge edge) {
        List<Edge> updated = new ArrayList<>(edges.size());
        for (Edge e : edges) {
            if (!e.equals(edge)) updated.add(e);
        }
        return new Design(name, nodes, List.copyOf(updated));
    }
}
