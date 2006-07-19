/**
 * 
 */
package at.dms.kjc.spacetime;

import java.util.Iterator;

import at.dms.kjc.sir.SIRFeedbackLoop;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRPipeline;
import at.dms.kjc.sir.SIRSplitJoin;
import at.dms.kjc.sir.SIRStream;
import at.dms.kjc.sir.lowering.fission.StatelessDuplicate;
import at.dms.kjc.sir.lowering.partition.WorkEstimate;
import at.dms.kjc.sir.lowering.partition.WorkList;
import java.util.*;
import at.dms.kjc.*;

/**
 * @author mgordon
 *
 */
public class DuplicateBottleneck {
    
    private Vector<Integer> sortedWorkEsts;
    private Vector<SIRFilter> sortedFilters;
    
    public DuplicateBottleneck() {
        
    }
    
    public void duplicate(SIRStream str, RawChip rawChip) {
        //keep duplicating until we cannot anymore!!
        //duplicateBottleneck(str);
        //while (duplicateBottleneck(str));
        percentStateless(str);
        //duplicateFilters(str, rawChip.getTotalTiles());
        assert KjcOptions.dup > 1;
        duplicateFilters(str, KjcOptions.dup);
    }
    
    private void percentStateless(SIRStream str) {
        sortedWorkEsts = new Vector<Integer>();
        sortedFilters = new Vector<SIRFilter>();
        //get the work estimate
        WorkEstimate work = WorkEstimate.getWorkEstimate(str);
        //find the ordering of filters
        walkSTR(str, work);
        
        int totalWork = 0;
        int statefulWork = 0; 
        
        for (int i = 0; i < sortedFilters.size(); i++) {
            totalWork += sortedWorkEsts.get(i).intValue();
            if (StatelessDuplicate.hasMutableState(sortedFilters.get(i)))
                    statefulWork += sortedWorkEsts.get(i).intValue();
        }
        System.out.println(" stateful work / total work = " + 
                (((double)statefulWork)) / (((double)totalWork)));
        
      
    }
    
    public SIRStream smartDuplication(SIRStream str, RawChip chip) {
        percentStateless(str);
        WorkEstimate work = WorkEstimate.getWorkEstimate(str);
        WorkList workList = work.getSortedFilterWork();
                //find the total work
        int totalWork = 0;
        for (int i = 0; i < workList.size(); i++) {
            SIRFilter filter = workList.getFilter(i);
            int filterWork = work.getWork(filter); 
            System.out.println("Sorted Work " + i + ": " + filter + " work " 
                    + filterWork + ", is fissable: " + StatelessDuplicate.isFissable(filter));
            totalWork += filterWork;
        }
        //find the ideal work distribution
        int idealWork = totalWork / chip.getTotalTiles();
        boolean change = false;
        System.out.println("Ideal Work: " + idealWork);
        
        for (int i = workList.size() - 1; i >= 0; i--) {
            SIRFilter filter = workList.getFilter(i);
            int filterWork = work.getWork(filter);
            if (!StatelessDuplicate.isFissable(filter))
                continue;
            System.out.println("Analyzing " + filter + " work = " + filterWork);
            if (filterWork >= 2 * idealWork) {
                int fissAmount = (int)Math.ceil(((double)filterWork) / ((double)idealWork));
                System.out.println("Fissing " + filter  + " " + fissAmount + 
                        " times (work was " + filterWork + ")");
                StatelessDuplicate.doit(filter, fissAmount);
                change = true;
            }
            else {
                System.out.println("Stop fissing, current filter work = " + filterWork);
                //since the list is sorted, nothing else will be over 
                //the threshold
                break;
            }
        }
        
        if (!change) {
            System.exit(0);
        }
        
        return str;
    }
    
    private boolean duplicateBottleneck(SIRStream str) {
        sortedWorkEsts = new Vector<Integer>();
        sortedFilters = new Vector<SIRFilter>();
        //get the work estimate
        WorkEstimate work = WorkEstimate.getWorkEstimate(str);
        //find the ordering of filters
        walkSTR(str, work);
        //return false if we cannot duplicate the bottleneck
        if (!StatelessDuplicate.isFissable(sortedFilters.get(0)))
            return false;
        
        for (int i = 0; i < sortedWorkEsts.size(); i++) {
            System.out.println(sortedFilters.get(i) + " = " + sortedWorkEsts.get(i));
        }
        
        //so the bottleneck if stateless, duplicate it as many times so 
        //that it is not the bottleneck anymore!
        
        int reps = (int)Math.round(0.5 + ((double)sortedWorkEsts.get(0)) / ((double)sortedWorkEsts.get(1)));
        
        StatelessDuplicate.doit(sortedFilters.get(0), reps);
        
        System.out.println(reps);
        
        //might be good for another round?
        return true;
    }
    
    private void duplicateFilters(SIRStream str, int reps) {
        if (str instanceof SIRFeedbackLoop) {
            SIRFeedbackLoop fl = (SIRFeedbackLoop) str;
            duplicateFilters(fl.getBody(), reps);
            duplicateFilters(fl.getLoop(), reps);
        }
        if (str instanceof SIRPipeline) {
            SIRPipeline pl = (SIRPipeline) str;
            Iterator iter = pl.getChildren().iterator();
            while (iter.hasNext()) {
                SIRStream child = (SIRStream) iter.next();
                duplicateFilters(child, reps);
            }
        }
        if (str instanceof SIRSplitJoin) {
            SIRSplitJoin sj = (SIRSplitJoin) str;
            Iterator iter = sj.getParallelStreams().iterator();
            while (iter.hasNext()) {
                SIRStream child = (SIRStream) iter.next();
                duplicateFilters(child, reps);
            }
        }
        if (str instanceof SIRFilter) {
            SIRFilter filter = (SIRFilter)str;
            if (StatelessDuplicate.isFissable(filter))
                StatelessDuplicate.doit(filter, reps);
        }
    } 
    
    private void walkSTR(SIRStream str, WorkEstimate work) {
        if (str instanceof SIRFeedbackLoop) {
            SIRFeedbackLoop fl = (SIRFeedbackLoop) str;
            walkSTR(fl.getBody(), work);
            walkSTR(fl.getLoop(), work);
        }
        if (str instanceof SIRPipeline) {
            SIRPipeline pl = (SIRPipeline) str;
            Iterator iter = pl.getChildren().iterator();
            while (iter.hasNext()) {
                SIRStream child = (SIRStream) iter.next();
                walkSTR(child, work);
            }
        }
        if (str instanceof SIRSplitJoin) {
            SIRSplitJoin sj = (SIRSplitJoin) str;
            Iterator iter = sj.getParallelStreams().iterator();
            while (iter.hasNext()) {
                SIRStream child = (SIRStream) iter.next();
                walkSTR(child, work);
            }
        }
        if (str instanceof SIRFilter) {
            SIRFilter filter = (SIRFilter)str; 
            int i;
            int workEst = work.getWork(filter);
            //find the right place to add this to
            for (i = 0; i < sortedFilters.size(); i++) {
                if (workEst > sortedWorkEsts.get(i).intValue())
                    break;
            }
            sortedFilters.add(i, filter);
            sortedWorkEsts.add(i, workEst);
        }
    } 
}
