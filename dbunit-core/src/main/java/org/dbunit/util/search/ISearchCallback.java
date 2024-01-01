/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2005, DbUnit.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */

package org.dbunit.util.search;

import java.util.SortedSet;

/**
 * Callback used by the search algorithms.<br>
 * This interface is responsible for providing the edges of the graph and it can
 * be notified of some events generated by the search.
 * 
 * @author Felipe Leme (dbunit@felipeal.net)
 * @version $Revision$
 * @since Aug 25, 2005
 */
public interface ISearchCallback {

    /**
     * Get the edges originating from a node.
     * 
     * @param fromNode node from
     * @return all edges originating from this node.
     * @throws Exception exception wrapper
     */
    SortedSet getEdges(Object fromNode) throws SearchException;

    /**
     * Notifies the callback that a node has been added to the search result.
     * 
     * @param fromNode node that has been added.
     * @throws Exception exception wrapper
     */
    void nodeAdded(Object fromNode) throws SearchException;

    /**
     * Decides if a node should be searched or not
     * 
     * @param node node to be filtered
     * @return true if the node should be searched
     * @throws Exception exception wrapper
     */
    boolean searchNode(Object node) throws SearchException;
}
