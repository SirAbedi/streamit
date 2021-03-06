package streamit.scheduler2.constrained;

import streamit.scheduler2.SDEPData;

public class InitUpstreamRestriction extends Restriction
{
    final SDEPData sdep;
    final StreamInterface parent;
    final InitDownstreamRestriction downstreamRestriction;

    public InitUpstreamRestriction(
                                   P2PPortal _portal,
                                   SDEPData _sdep,
                                   StreamInterface _parent,
                                   InitDownstreamRestriction _downstreamRestriction,
                                   Restrictions restrictions)
    {
        super(_portal.getUpstreamNode(), _portal);

        useRestrictions(restrictions);

        sdep = _sdep;
        parent = _parent;
        downstreamRestriction = _downstreamRestriction;

        int minLatency = portal.getMinLatency();
        int maxLatency = portal.getMaxLatency();

        if (portal.isDownstream())
            {
                if (portal.getMaxLatency() >= 0)
                    {
                        // downstream, positive latency
                        ERROR("not tested");

                        // there is no actual initial restriction on the number 
                        // of executions that the upstream filter must complete
                        // before this portal has a valid amount of data

                        // this means that we want to have maxExecution set
                        // to 0, so that I will use my usual framework to
                        // right away change the restriction to a steady-state
                        // restriction

                        setMaxExecutions(0);
                    }
                else
                    {
                        // downstream, negative latency
                        assert portal.getMinLatency() < 0;

                        assert false: "not implemented";
                    }
            }
        else
            {
                // upstream, positive latency
                assert portal.getMinLatency() >= 0;

                // this type of portal does not cause me to have to execute
                // the upstream filter a certain # of times before being
                // ready to correctly process messages for this portal.
                // that means that I can just set my max executions to 0,
                // and get removed immediately :)
            
                setMaxExecutions (0);
            }
    }

    public boolean notifyExpired()
    {
        // okay, I now have executed the entire initialization.
        // get the parent stream to insert proper steady-state
        // these steady state restrictions will replace this
        // restriction, thus I don't have to tell the restrictions
        // manager to remove me.
        portal.getParent().initRestrictionsCompleted(portal);

        return false;
    }
}
