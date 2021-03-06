package at.dms.kjc.spacetime.switchIR;

//Ports that can be used as input
public class SwitchIPort implements SwitchSrc {
    public static final SwitchIPort CSTO=new SwitchIPort("$csto");
    public static final SwitchIPort N=new SwitchIPort("$cNi");
    public static final SwitchIPort E=new SwitchIPort("$cEi");
    public static final SwitchIPort S=new SwitchIPort("$cSi");
    public static final SwitchIPort W=new SwitchIPort("$cWi");
    public static final SwitchIPort N2=new SwitchIPort("$cNi2");
    public static final SwitchIPort E2=new SwitchIPort("$cEi2");
    public static final SwitchIPort S2=new SwitchIPort("$cSi2");
    public static final SwitchIPort W2=new SwitchIPort("$cWi2");
    public static final SwitchIPort SWI1=new SwitchIPort("$swi1");
    public static final SwitchIPort SWI2=new SwitchIPort("$swi2");
    
    private String reg;
    
    private SwitchIPort(String reg) {
        this.reg=reg;
    }
    
    public String toString() {
        return reg;
    }
    
    public static SwitchIPort getIPort(String dir) 
    {
        if (dir == "st")
            return CSTO;
        if (dir == "N")
            return N;
        if (dir == "E")
            return E;
        if (dir == "S")
            return S;
        if (dir == "W")
            return W;
    
        assert false : "invalid direction for getIPort";
        return null;
    }
    
    public static SwitchIPort getIPort2(String dir) 
    {
        if (dir == "st")
            return CSTO;
        if (dir == "N")
            return N2;
        if (dir == "E")
            return E2;
        if (dir == "S")
            return S2;
        if (dir == "W")
            return W2;
        assert false : "invalid direction for getIPort2";
        return null;
    }   
}
