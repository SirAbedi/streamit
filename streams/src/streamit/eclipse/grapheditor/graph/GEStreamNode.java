/*
 * Created on Jun 20, 2003
 */ 
package streamit.eclipse.grapheditor.graph;
 
import java.awt.Color;
import java.awt.Rectangle;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map;

import org.jgraph.JGraph;
import org.jgraph.graph.DefaultEdge;
import org.jgraph.graph.DefaultGraphCell;
import org.jgraph.graph.DefaultPort;
import org.jgraph.graph.GraphConstants;
import org.jgraph.graph.Port;

/**
 * GEStreamNode is the graph internal representation of a node. 
 * @author jcarlos
 */
public abstract class GEStreamNode extends DefaultGraphCell implements Serializable{
	
	
	/**
	 * The name of the GEStreamNode.
	 */
	protected String name;	

	/**
	 * The name without the underscore and the numbers that follow it. 
	 */
	protected String nameNoID;

	/**
	 * The type of the GEStreamNode. Must be one of the types specified by GEType.
	 */
	protected String type;
	
	/**
	 * The children of the GEStreamNode. 
	 */
	protected ArrayList children;
	
	/**
	 * The immediate GEStreamNode that contains this GEStreamNode.
	 */
	protected GEStreamNode encapsulatingNode;	
	
	
	/**
	 * The port of the GEStreamNode (used by JGraph).
	 */
	protected DefaultPort port;
		
	/**
	 * The information that this GEStreamNode contains. Depending on the type,
	 * this data will be different. 
	 */
	protected String info;
	
	/**
	 * Boolean that determines if the information of the GEStreamNode is being displayed.
	 */
	protected boolean isInfoDisplayed;

	/**
	 * Boolean that determines if the GEStreamNode is connected to other elements in 
	 * the graph. isConnected is false whenever the GEStreamNode has no edges connected
	 * to it in either its expanded or collapsed state. Default value is false (the value
	 * must be set explicitly whenever the node has been connected to a different node). 
	 */
	protected boolean isNodeConnected;

	/**
	 * The input tape value for the StreamIt representation of the GEPhasedFilter.
	 * The default value for the inputTape should be void. 
	 */
	protected String inputTape;
	
	/**
	 * The output tape value for the StreamIt representation of the GEPhasedFilter.
	 * The default value for the outputTape should be void. 
	 */	
	protected String outputTape;
	
	/**
	 * The arguments that must be passed to the StreamIt representation of the GEPhasedFilter.
	 * The default value for args is: no arguments (empty list).
	 */
	protected ArrayList args;
	
	/**
	 * The level at which the GEStreamNode is located. The TopLevel node is at level zero, so 
	 * every immediate node it contains is at level 1. The childrent of containers at level 1
	 * will have level corresponding to 2, and so on. The default value of the level is 1 
	 * (so that the node's parent will be the Toplevel node).
	 */
	protected int level;


	protected ArrayList sourceEdges;
	protected ArrayList targetEdges;

	/**
	 * GEStreamNode constructor.
	 * @param type The type of the GEStreamNode (must be defined as a GEType)
	 * @param name The name of the GEStreamNode.
	 */
	public GEStreamNode(String type, String name)
	{
		super("<HTML><H4>"+name+"</H4></html>");
		this.type = type;
		this.children = new ArrayList();
		this.name = name;
		this.setInfo(name);
		this.isInfoDisplayed = true;
		this.encapsulatingNode = null;
		this.sourceEdges = new ArrayList();
		this.targetEdges = new ArrayList();
		this.args = new ArrayList();
		this.inputTape =  "void";
		this.outputTape = "void";
		this.isNodeConnected = false;
		this.level = 1;
		setNameNoID();
	}

	/**
	 * Set the name without the ID (this means without the underscore
	 * and without the numbers that follow the underscore.
	 */
	private void setNameNoID()
	{
		int indexUnderscore = this.name.lastIndexOf("_");
		if (indexUnderscore != -1)
		{
			this.nameNoID = this.name.substring(0,indexUnderscore); 
		}
		else
		{
			this.nameNoID = this.name;
		}	
	}

	/**
 	 * Add a child to this GEStreamNode.
 	 * @return True if teh child was added succesfully, otherwise false.
 	 */
	public boolean addChild(GEStreamNode strNode)
	{
		return this.children.add(strNode);
	}
		
	/**
	 * Add a child to this GEStreamNode at index i.
	 * @return True if teh child was added succesfully, otherwise false.
	 */
	public void addChild(GEStreamNode strNode, int i)
	{
		this.children.add(i, strNode);
	}
		
		
	/**
 	 * Get the children of <this>.
 	 * @return An ArrayList with the children of the GEStreamNode. 
 	 */
	public ArrayList getSuccesors()
	{
		return this.children;
	}

	/**
 	 * Get the name of this GEStreamNode.
 	 * @return The name of this GEStreamNode.
	 */
	public String getName()
	{
		return this.name;	 
	}
	
	/**
	 * Get the name with no ID (without the underscore 
	 * and the numbers that follow it).
	 * @return The name with no ID of this GEStreamNode.
	 */
	public String getNameNoID()
	{
		return this.nameNoID;
	}
	
	
	/**
 	* Get the type of this GEStreamNode.
 	* @return The type of this GEStreamNode.
 	*/	
	public String getType()
	{
		return this.type;	
	}
	
	/**
	 * Get the port of <this>.
	 * @return The port that corresponds to the GEStreamNode
	 */
	public Port getPort()
	{
		return this.port;
	}

	/**
	 * Set the port for <this>
	 * @param port Set the GEStreamNode's port to <port>.
	 */
	public void setPort(DefaultPort port)
	{
		this.port = port;
	}

	/**
	 * Get the input tape of <this>
	 * @return The input tape that corresponds to the GEStreamNode.
	 */
	public String getInputTape()
	{
		return this.inputTape;
	}

	/**
	 * Set the input tape of <this>.
	 * @param in String value to which GEStreamNode's input tape will be set.
	 */
	public void setInputTape(String in)
	{
		this.inputTape = in;
	}

	/**
	 * Get the output tape of <this>
	 * @return The output tape that corresponds to the GEStreamNode.
	 */	
	public String getOutputTape()
	{
		return this.outputTape;
	}

	/**
	 * Set the output tape of <this>.
	 * @param in String value to which GEStreamNode's output tape will be set. 
	 */
	public void setOutputTape(String out)
	{
		this.outputTape = out;
	}



	/**
	 * Returns true when this GEStreamNode is connected to other GEStreamNode in 
	 * either its expanded or collapsed states. Otherwise, return false.
	 * @return boolean
	 */
	public boolean isNodeConnected()
	{
		return this.isNodeConnected;
	}

	/**
	 * Set the field that determines if this GEStreamNode is connected by an 
	 * edge to any other other GEStreamNode.
	 * @param bool
	 */
	public void setIsNodeConnected(boolean bool)
	{
		this.isNodeConnected = bool;
	}
	
	/**
	 * Sets the node that encapsulates this
	 * @param node The GEStreamNode that encapsulates this
	 */
	public void setEncapsulatingNode(GEStreamNode node)
	{
		this.encapsulatingNode = node;
	}
	
	/**
	 * Gets the encapsulating node of this
	 * @return The encapsulating node of GEStreamNode
	 */
	public GEStreamNode getEncapsulatingNode()
	{
		return this.encapsulatingNode;
	}
	
	/**
	 * Get the depth level at which this is located
	 * @return level of the GEStreamNode
	 */
	public int getDepthLevel()
	{
		return this.level;
	}
	
	/**
	 * Set the depth level at which this is located
	 * @param lvl Level to which the GEStreamNode is locate.
	 */
	public void setDepthLevel(int lvl)
	{
		this.level = lvl;
	}
	
	/**
	 * Get the info of <this>.
	 * @return The info of this GEStreamNode.
	 */
	public String getInfo()
	{
		return this.info;	
	}

	/**
	 * Set the info of <this>.
	 * @param info Set the info of the GEStreamNode.
	 */
	public void setInfo(String info)
	{
		this.info = info;
	}

	/**
	 * Get the info of <this> as an HTML label.
	 * @return The info of the GEStreamNode as an HTML label.
	 */
	public String getInfoLabel()
	{
		return new String("<HTML><H4>"+ this.name + "<BR>" + this.info + "</H4></HTML>");
	}
	
	/**
	 * Get the name of <this> as an HTML label.
	 * @return The name of the GEStreamNode as an HTML label.
	 */
	public String getNameLabel()
	{
		return new String("<HTML><H4>"+ this.name + "</H4></HTML>");
	}
	
	/**
	 * Add <target> to the list of edges that have this GEStreamNode as its target.
	 * @param target DefautltEdge that has <this> as its target. 
	 */	
	public void addTargetEdge(DefaultEdge target)
	{
		this.targetEdges.add(target);
	}

	/**
	 * Add <source> to the list of edges that have this GEStreamNode as its source.
	 * @param source DefautltEdge that has <this> as its source. 
	 */		
	public void addSourceEdge(DefaultEdge source)
	{
		this.sourceEdges.add(source);
	}

	/**
	 * Remove <target> from the list of edges that have this GEStreamNode as its target.
	 * @param target DefautltEdge that has <this> as its target. 
	 */	
	public void removeTargetEdge(DefaultEdge target)
	{
		this.targetEdges.remove(target);
	}
	
	/**
	 * Remove <source> from the list of edges that have this GEStreamNode as its source.
	 * @param source DefautltEdge that has <this> as its source. 
	 */		
	public void removeSourceEdge(DefaultEdge source)
	{
		this.sourceEdges.remove(source);
	}
	
	/**
	 * Get a list of edges that have this GEStreamNode as its target. 
	 * @return ArrayList that containes the edges with <this> as its target.
	 */
	public ArrayList getTargetEdges()
	{
		return this.targetEdges;	
	}
	
	/**
	 * Get a list of edges that have this GEStreamNode as its source. 
	 * @return ArrayList that containes the edges with <this> as its source.
	 */
	public ArrayList getSourceEdges()
	{
		return this.sourceEdges;
	}
	
	/**
	 * Write the textual representation of the arguments that correspond 
	 * to this GEStreamNode. The arguments will be written in the form:
	 * "(arg1, arg2, ... , argn)".
	 * @param out PrintWriter used to output text representation of args. 
	 */
	protected void outputArgs(PrintWriter out)
	{
		out.print(" ( ");
		
		int numArgs = this.args.size();	
		for (int i = 0; i < numArgs; i++)
		{
			out.print((String) args.get(i));	
			if (i != numArgs - 1)
			{
				out.print(",");
			}
		}
		out.print(" ) ");		
	}

	/**
	 * Highlight or unhighlight the node
	 * @param graphStruct GEStreamNode to be highlighted or unhighlighted
	 * @param doHighlight The node gets highlighted when true, and unhighlighted when false.
	 */
	public void highlight(GraphStructure graphStruct, boolean doHighlight)
	{
		Map change = GraphConstants.createMap();
		
		
		//System.out.println("ENTERED HIGHLIGHT NODE CODE");
		//if (doHighlight)
		//demoadd
		if ((doHighlight) && !(this instanceof GESplitJoin) && !(this instanceof GEPipeline))
		{
			GraphConstants.setBorderColor(change, Color.yellow);
			GraphConstants.setLineWidth(change, 4);
		}
		else
		{
			if ((this instanceof GEPhasedFilter) || (this instanceof GESplitter) || (this instanceof GEJoiner))
			{
				GraphConstants.setBorderColor(change, Color.white);
			}
			else if (this instanceof GEPipeline)
			{
				GraphConstants.setBorderColor(change, Color.red.darker());
			}
			else if (this instanceof GESplitJoin)
			{
				GraphConstants.setBorderColor(change, Color.blue.darker());
			}
			else if (this instanceof GEFeedbackLoop)
			{
				GraphConstants.setBorderColor(change, Color.green.darker());
			}
			
			//GraphConstants.setRemoveAttributes(this.attributes, new Object[] {GraphConstants.BORDER,GraphConstants.BORDERCOLOR});
		}
	
		Map nest = new Hashtable ();
		nest.put(this, change);
		graphStruct.getGraphModel().edit(nest, null, null, null);
		
		
		//TODO MIGHT BE WRONG BECAUSE WE ARE INSERTING THINGS TWICE
		/*(graphStruct.getGraphModel()).edit(graphStruct, null, null, null, null);
		/*
		Object[] obj = graphStruct.getJGraph().getRoots();
		for (int i = 0; i < obj.length; i++)
		{		
			System.out.println("THE CELLS IN THE GRAPH  "+ i + " "+ obj[i].toString());
		}*/
	}
	
	
	/**
	 * Construct the GEStreamNode. The subclasses must implement this method according to
	 * their specific needs.
	 * @param graphStruct GraphStructure to which GEStreamNode belongs.
	 * @return GEStreamNode 
	 */
	abstract GEStreamNode construct(GraphStructure graphStruct, int level);
	
	/**
	 * Expand or collapse the GEStreamNode structure depending on wheter it was already 
	 * collapsed or expanded. 
	 * @param jgraph The JGraph that will be modified to allow the expanding/collapsing.
	 */	
	abstract public void collapseExpand(JGraph jgraph);
	abstract public void collapse(JGraph jgraph);
	abstract public void expand(JGraph jgraph);


	/**
	 * Set the attributes necessary to display the GEStreamNode.
	 * @param graphStruct GraphStructure
	 * @param bounds Rectangle
	 */
	abstract public void initDrawAttributes(GraphStructure graphStruct, Rectangle bounds);



	/**
	 * Hide the GEStreamNode in the display. Note that some nodes cannot be hidden or 
	 * they cannot be made visible.
	 * @return true if it was possible to hide the node; otherwise, return false.
	 */
	abstract public boolean hide();

	/**
	 * Make the GEStreamNode visible in the display. Note that some nodes cannot be hidden or 
	 * they cannot be made visible. 
	 * @return true if it was possible to make the node visible; otherwise, return false.
	 */	
	abstract public boolean unhide();
	
	/** Returns a list of nodes that are contained by this GEStreamNode. If this GEStreamNode is
	 * not a container node (can't have any contained elements), then null is returned.
	 * @return ArrayList of contained elements. If <this> is not a container, return null.
	 */
	abstract public ArrayList getContainedElements();

	/**
	 * Writes the textual representation of the GEStreamNode using the PrintWriter specified by out. 
	 * In this case, the textual representation corresponds to the the StreamIt source code 
	 * equivalent of the GEStreamNode. 
	 * @param out PrintWriter that is used to output the textual representation of the graph.  
	 */
	abstract public void outputCode(PrintWriter out);


}