package de.l3s.icrawl.contentanalysis;

import java.util.Iterator;
import java.util.Set;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;

import static java.util.Objects.requireNonNull;

/**
 * Iterate over all nodes of a JSoup DOM tree.
 *
 * Similar to {@link org.w3c.dom.traversal.TreeWalker}, but with simpler
 * interface and without unused functionality.
 */
class TreeWalker implements Iterable<Node> {

    private class TreeIterator extends AbstractIterator<Node> {
        private Node currentNode;
        private int depth = 0;
        private boolean finished = false;

        public TreeIterator(Node n) {
            currentNode = n;
        }

        @Override
        protected Node computeNext() {
            Node returnedNode = currentNode;
            if (finished) {
                return endOfData();
            }
            do {
                if (currentNode.childNodeSize() > 0 && !isSkippedNode(currentNode)) {
                    currentNode = currentNode.childNode(0);
                    depth++;
                } else {
                    while (currentNode.nextSibling() == null && depth > 0) {
                        currentNode = currentNode.parentNode();
                        depth--;
                    }
                    if (currentNode == startNode) {
                        finished = true;
                        break;
                    }

                    currentNode = currentNode.nextSibling();
                }
            } while (isSkippedNode(currentNode));
            return returnedNode != null ? returnedNode : endOfData();
        }

        boolean isSkippedNode(Node node) {
            return node instanceof Element && skippedElements.contains(((Element) node).tagName());
        }

        @Override
        public String toString() {
            Node prevCurrentNode = currentNode;
            int prevDepth = depth;
            boolean prevFinished = finished;
            try {
                return Iterators.toString(this);
            } finally {
                currentNode = prevCurrentNode;
                depth = prevDepth;
                finished = prevFinished;
            }
        }
    }

    private final Set<String> skippedElements;
    private final Node startNode;

    public TreeWalker(Node n, Set<String> skippedElements) {
        this.startNode = n;
        this.skippedElements = requireNonNull(skippedElements);
    }

    @Override
    public Iterator<Node> iterator() {
        return new TreeIterator(startNode);
    }

}
