/*
 * Created on Jun 20, 2003
 */
package streamit.eclipse.grapheditor.graph;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;

import javax.swing.JLabel;

import org.jgraph.JGraph;
import org.jgraph.graph.ConnectionSet;
import org.jgraph.graph.DefaultEdge;
import org.jgraph.graph.DefaultPort;
import org.jgraph.graph.GraphConstants;

import streamit.eclipse.grapheditor.graph.utils.JGraphLayoutManager;

/**
 * GEPipeline is the graph internal representation of a pipeline. 
 * @author jcarlos
 */
public class GEPipeline extends GEStreamNode implements Serializable{
			
	private GEStreamNode lastNode;	
	
	/**
	 * The sub-graph structure that is contained within this pipeline.
	 * This subgraph is hidden when the pipeline is collapse and 
	 * visible when expanded. 
	 */
	private GraphStructure localGraphStruct;

	/**
	 * Boolean that specifies if the elements contained by the Pipeline are 
	 * displayed (it is expanded) or they are hidden (it is collapsed).
	 */
	public boolean isExpanded;
	
	/**
	 * GEPipeline constructor.
	 * @param name The name of this GEPipeline.
	 */
	public GEPipeline(String name)
	{
		super(GEType.PIPELINE, name);
		localGraphStruct = new GraphStructure();	
	}


	public GEPipeline(String name, GraphStructure gs)
	{
		super(GEType.PIPELINE, name);
		localGraphStruct = gs;	
	}

	/**
 	 * Constructs the pipeline and returns <this> so that the GEPipeline can 
 	 * be connected to its succesor and predecessor.
 	*/
	public GEStreamNode construct(GraphStructure graphStruct, int lvel)
	{
		System.out.println("Constructing the pipeline" +this.getName());
		boolean first = true;
		this.level = lvel;
		
		graphStruct.addToLevelContainer(this.level, this);
		lvel++;
		graphStruct.getJGraph().addMouseListener(new JGraphMouseAdapter(graphStruct.getJGraph(), graphStruct));
		
		this.localGraphStruct = graphStruct;
		
		//graphStruct.getJGraph().getGraphLayoutCache().setVisible(this, true);
		//this.localGraphStruct.setJGraph(graphStruct.getJGraph());		
		
		ArrayList nodeList = (ArrayList) this.getSuccesors();
		Iterator listIter =  nodeList.listIterator();
	
		while(listIter.hasNext())
		{
			GEStreamNode strNode = (GEStreamNode) listIter.next();
			GEStreamNode lastTemp = strNode.construct(graphStruct, lvel); //GEStreamNode lastTemp = strNode.construct(this.localGraphStruct);
			
			if(!first)
			{
				System.out.println("Connecting " + lastNode.getName()+  " to "+ strNode.getName());		 
				graphStruct.connectDraw(lastNode, strNode); //this.localGraphStruct.connectDraw(lastNode, strNode);
			}
			lastNode = lastTemp;
			first = false;
		}
	
		//this.localGraphStruct.getGraphModel().insert(this.localGraphStruct.getCells().toArray(), this.localGraphStruct.getAttributes(), this.localGraphStruct.getConnectionSet(), null, null);
		graphStruct.getGraphModel().insert(graphStruct.getCells().toArray(), graphStruct.getAttributes(), graphStruct.getConnectionSet(), null, null);
		this.initDrawAttributes(graphStruct, new Rectangle(new Point(100,100)));

		if (graphStruct.getTopLevel() == this)
		{
			graphStruct.getJGraph().getGraphLayoutCache().setVisible(new Object[]{this}, nodeList.toArray());
		}
				
		return this;
	}	
	
	/**
	 * Initialize the default attributes that will be used to draw the GEPipeline.
	 * @param graphStruct The GraphStructure that will have its attributes set.
	 */	
	public void initDrawAttributes(GraphStructure graphStruct, Rectangle bounds)
	{
		this.port = new DefaultPort();
		this.add(this.port);

		(graphStruct.getAttributes()).put(this, this.attributes);
	//	GraphConstants.setAutoSize(this.attributes, true);	
		//GraphConstants.setBorder(this.attributes , BorderFactory.createLineBorder(Color.blue));
		
		// demoremove GraphConstants.setBorderColor(this.attributes, Color.red.darker());
		// demoremove GraphConstants.setLineWidth(this.attributes, 4);
		GraphConstants.setBounds(this.attributes, bounds);
		GraphConstants.setVerticalTextPosition(this.attributes, JLabel.TOP);
		(graphStruct.getGraphModel()).insert(new Object[] {this}, null, null, null, null);	
	}
			
	/**
	 * Expand or collapse the GEStreamNode structure depending on wheter it was already 
	 * collapsed or expanded. 
	 * @param jgraph The JGraph that will be modified to allow the expanding/collapsing.
	 */
	public void collapseExpand(JGraph jgraph)
	{
		if (isExpanded)
		{
			this.collapse(jgraph);
			isExpanded = false;
		}
		else
		{
			this.expand(jgraph);
			isExpanded = true;
		}		
	}
	/**
	 * Expand the GEPipeline so that the nodes that it contains become visible.
	 */
	public void expand(JGraph jgraph)
	{
		Object[] nodeList = this.getSuccesors().toArray();
		ConnectionSet cs = this.localGraphStruct.getConnectionSet();	
		jgraph.getGraphLayoutCache().setVisible(nodeList, true);
		
		Iterator eIter = localGraphStruct.getGraphModel().edges(this.getPort());
		ArrayList edgesToRemove =  new ArrayList();
		GEStreamNode firstInPipe = (GEStreamNode) nodeList[0];
		GEStreamNode finalInPipe = (GEStreamNode) nodeList[nodeList.length-1];
			
		while (eIter.hasNext())
		{
			DefaultEdge edge = (DefaultEdge) eIter.next();
				
			Iterator sourceIter = this.getSourceEdges().iterator();	
			System.out.println(" edge hash" +edge.hashCode());
			while (sourceIter.hasNext())
			{
				DefaultEdge s = (DefaultEdge) sourceIter.next();
				System.out.println(" s hash" +s.hashCode());
				if (s.equals(edge))
				{
						
					System.out.println("source edges were equal");
					cs.disconnect(edge, true);
					cs.connect(edge, finalInPipe.getPort(), true);	
					finalInPipe.addSourceEdge(s);
					edgesToRemove.add(s);
				}
			}
			
			Iterator targetIter = this.getTargetEdges().iterator();
			while(targetIter.hasNext())
			{
				DefaultEdge t = (DefaultEdge) targetIter.next();
				System.out.println(" t hash" +t.hashCode());
				if(t.equals(edge))
				{
					System.out.println("target edges were equal");
					cs.disconnect(edge,false);
					cs.connect(edge, firstInPipe.getPort(),false);
					firstInPipe.addTargetEdge(t);
					edgesToRemove.add(t);
				}
			}	
			
			Object[] removeArray = edgesToRemove.toArray();
			for(int i = 0; i<removeArray.length;i++)
			{
				this.removeSourceEdge((DefaultEdge)removeArray[i]);
				this.removeTargetEdge((DefaultEdge)removeArray[i]);
			}
		}
		this.localGraphStruct.getGraphModel().edit(null, cs, null, null);
		//this.hide(); //jgraph.getGraphLayoutCache().setVisible(new Object[]{this}, false);
		
		for (int i = level; i >= 0; i--)
		{
			this.localGraphStruct.hideContainersAtLevel(i);
		}
		
		//CHANGE 12/2/03 JGraphLayoutManager manager = new JGraphLayoutManager(this.localGraphStruct.getJGraph());
		JGraphLayoutManager manager = new JGraphLayoutManager(jgraph);
		manager.arrange();
		setLocationAfterExpand();
	}	

	/**
	 * Collapse the GEPipeline so that the nodes it contains become invisible. 
	 */
	public void collapse(JGraph jgraph)
	{
		Object[] nodeList = this.getSuccesors().toArray();
		ConnectionSet cs = this.localGraphStruct.getConnectionSet();	
		this.unhide(); //jgraph.getGraphLayoutCache().setVisible(new Object[]{this}, true);
		
		GEStreamNode firstInPipe = (GEStreamNode) nodeList[0];
		GEStreamNode finalInPipe = (GEStreamNode) nodeList[nodeList.length-1];
		
		Iterator initialEdgeIter = localGraphStruct.getGraphModel().edges(firstInPipe.getPort());
		Iterator finalEdgeIter = localGraphStruct.getGraphModel().edges(finalInPipe.getPort());
		
		ArrayList edgesToRemove =  new ArrayList();
		
		
		while (initialEdgeIter.hasNext())
		{
			DefaultEdge edge = (DefaultEdge) initialEdgeIter.next();
				
			Iterator sourceIter = finalInPipe.getSourceEdges().iterator();
			while(sourceIter.hasNext())
			{
				DefaultEdge target = (DefaultEdge) sourceIter.next();
				if(target.equals(edge))
				{
					System.out.println("source equals edge");
					cs.disconnect(edge, true);
					cs.connect(edge, this.getPort(), true);
					this.addSourceEdge(edge);
					edgesToRemove.add(edge);
				}
			}
			
			Iterator targetIter = firstInPipe.getTargetEdges().iterator();	
			while(targetIter.hasNext())
			{
				DefaultEdge source = (DefaultEdge) targetIter.next();
				if (source.equals(edge))
				{
					System.out.println("target equals target");
					cs.disconnect(edge,false);
					cs.connect(edge, this.getPort(),false);
					this.addTargetEdge(edge);
					edgesToRemove.add(edge);
				}
			}
		}
		
		while (finalEdgeIter.hasNext())
		{
			DefaultEdge edge = (DefaultEdge) finalEdgeIter.next();
			
			Iterator sourceIter = finalInPipe.getSourceEdges().iterator();
			while(sourceIter.hasNext())
			{
				DefaultEdge source = (DefaultEdge) sourceIter.next();
				if(source.equals(edge))
				{
					System.out.println("source equals edge");
					cs.disconnect(edge, true);
					cs.connect(edge, this.getPort(), true);
					this.addSourceEdge(edge);
					edgesToRemove.add(edge);
				}
			}
			
			Iterator targetIter = firstInPipe.getTargetEdges().iterator();	
			while(targetIter.hasNext())
			{
				DefaultEdge target = (DefaultEdge) targetIter.next();
				if (target.equals(edge))
				{
					System.out.println("target equals target");
					cs.disconnect(edge,false);
					cs.connect(edge, this.getPort(),false);
					this.addTargetEdge(edge);
					edgesToRemove.add(edge);
				}
			}			
		}	
			
		Object[] removeArray = edgesToRemove.toArray();
		for(int i = 0; i<removeArray.length;i++)
		{
			firstInPipe.removeSourceEdge((DefaultEdge)removeArray[i]);
			firstInPipe.removeTargetEdge((DefaultEdge)removeArray[i]);
			finalInPipe.removeSourceEdge((DefaultEdge)removeArray[i]);
			finalInPipe.removeTargetEdge((DefaultEdge)removeArray[i]);

		}

		GraphConstants.setAutoSize(this.attributes, true);			
		this.localGraphStruct.getGraphModel().edit(localGraphStruct.getAttributes(), cs, null, null);
	
		System.out.println("THE NODELIST " +nodeList.toString() + " in Pipeline " + this.name);
		jgraph.getGraphLayoutCache().setVisible(nodeList, false);
		
		for (int i = level - 1; i >= 0; i--)
		{
			this.localGraphStruct.hideContainersAtLevel(i);
		}	
		
		//JGraphLayoutManager manager = new JGraphLayoutManager(this.localGraphStruct.getJGraph());
		JGraphLayoutManager manager = new JGraphLayoutManager(jgraph);
		manager.arrange();	
		
		for (int i = level - 1; i >= 0; i--)
		{
			this.localGraphStruct.setLocationContainersAtLevel(i);
		}	
	}
	
	/**
	 * Set the location of all of the containers that might have been affected by the
	 * expansion. This includes all of the containers located at the current level
	 * and below (since everything below is expanded and the location of these
	 * containers will have to be set). 
	 */
	private void setLocationAfterExpand()
	{
		for (int i = level; i >= 0; i--)
		{
			this.localGraphStruct.setLocationContainersAtLevel(i);
		}
	}
	
	
	/**
 	 * Writes the textual representation of the GEStreamNode using the PrintWriter specified by out. 
 	 * In this case, the textual representation corresponds to the the StreamIt source code 
 	 * equivalent of the GEStreamNode. 
 	 * @param out PrintWriter that is used to output the textual representation of the graph.  
 	 */
	public void outputCode(PrintWriter out)
	{
		String tab = "     ";
		
		out.println();
		out.print(this.inputTape + "->" + this.outputTape + " pipeline " + this.name);
	
		if (this.args.size() > 0)
		{
			this.outputArgs(out);
		}
		out.println(" { ");	
				
		Iterator childIter  = this.children.iterator();
		while(childIter.hasNext())
		{
			out.println(tab + "add " + ((GEStreamNode) childIter.next()).name + "();");
		}
		
		out.println("}");
		out.println();
	}
	
	/**
 	 * Hide the GEStreamNode in the display. Note that some nodes cannot be hidden or 
 	 * they cannot be made visible.
 	 * @return true if it was possible to hide the node; otherwise, return false.
 	 */
	public boolean hide()
	{
		this.localGraphStruct.getJGraph().getGraphLayoutCache().
			setVisible(new Object[]{this}, false);
		return true;
	}

	/**
 	 * Make the GEStreamNode visible in the display. Note that some nodes cannot be hidden or 
 	 * they cannot be made visible. 
 	 * @return true if it was possible to make the node visible; otherwise, return false.
 	 */	
	public boolean unhide()
	{
		this.localGraphStruct.getJGraph().getGraphLayoutCache().
			setVisible(new Object[]{this}, true);
		return true;
	}	
	
	/** Returns a list of nodes that are contained by this GEStreamNode. If this GEStreamNode is
 	 * not a container node, then a list with no elements is returned.
 	 * @return ArrayList of contained elements. If <this> is not a container, return empty list.
 	 */
	public ArrayList getContainedElements()
	{
		return this.getSuccesors();
	}	
}