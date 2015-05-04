package bufmgr;

import global.GlobalConst;
import global.PageId;
import global.SystemDefs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import chainexception.ChainException;
import diskmgr.DiskMgrException;
import diskmgr.FileIOException;
import diskmgr.InvalidPageNumberException;
import diskmgr.InvalidRunSizeException;
import diskmgr.Page;

public class BufMgr implements GlobalConst {
	private byte[][] bufpool;
	private BufDescr[] bufDescrArray;
	private HashMap<PageId, Integer> idIndexMap;
	private int numBufs;
	private String replaceArg;
	private ArrayList<Integer> replacementQueue = new ArrayList<Integer>();
	private ArrayList<Integer> lovedQueue = new ArrayList<Integer>();
    private ArrayList<Integer> originalQueue = new ArrayList<Integer>();
	public BufMgr(int numBufs, String replaceArg) {
		this.numBufs = numBufs;
		this.replaceArg = replaceArg;
		bufpool = new byte[numBufs][MAX_SPACE];
		bufDescrArray = new BufDescr[numBufs];
		idIndexMap = new HashMap<PageId, Integer>();
		if(!(replaceArg.equalsIgnoreCase("FIFO")||replaceArg.equalsIgnoreCase("LRU")||
		replaceArg.equalsIgnoreCase("MRU")||replaceArg.equalsIgnoreCase("love/hate")))
		{
		    replaceArg = "LRU";
		}
		
	    for(int i =0 ; i<numBufs; i++){
	        originalQueue.add(i);
	    }
		
	}

	public void pinPage(PageId pageno, Page page /* return */, boolean emptyPage) throws InvalidPageNumberException, FileIOException, IOException {
		// TODO Auto-generated method stub
		if (idIndexMap.containsKey(pageno)) {
			int bufIndex = idIndexMap.get(pageno);
			bufDescrArray[bufIndex].increasePinCount();
			pagePinned(bufIndex);
			page.setpage(bufpool[bufIndex]);
			return;
		}

		int newFrameNo = getCandidateFrame(); // here needs handling if there is
		// no frames free
		
		Page tempPage = new Page();
		if (bufDescrArray[newFrameNo].isDirtyBit()) {
			flushPage(bufDescrArray[newFrameNo].getPageID());
		}

		try {
			SystemDefs.JavabaseDB.read_page(pageno, tempPage);
		} catch (InvalidPageNumberException | FileIOException | IOException e) {
			throw e;// ("BufMgr.java: pinPage() failed", e.getCause());
		}
		
		removeCandidateFromQueue(newFrameNo);
		bufpool[newFrameNo] = tempPage.getpage();
		idIndexMap.remove(bufDescrArray[newFrameNo].getPageID());
		idIndexMap.put(pageno, newFrameNo);
		bufDescrArray[newFrameNo].setPageID(pageno);
		bufDescrArray[newFrameNo].setDirtyBit(false);
		bufDescrArray[newFrameNo].setPinCount(1);
		bufDescrArray[newFrameNo].setLoved(false);
		pagePinned(newFrameNo);
		
		removeCandidateFromOriginalQueue();
	}

    public void removeCandidateFromQueue(int frameId)
    {
        replacementQueue.remove(replacementQueue.indexOf(frameId));
    }
    public void removeCandidateFromOriginalQueue()
    {
        if(!originalQueue.isEmpty())
        originalQueue.remove(0);
    }

	public void pagePinned(int frameIndex) {
	    if(bufDescrArray[frameIndex].getPinCount()==1)
	    {
	        numBufs--;
	    }
		if (replaceArg.equalsIgnoreCase("FIFO")) {
		    if(!replacementQueue.contains(frameIndex))
            replacementQueue.add(frameIndex);
		}else if(replaceArg.equalsIgnoreCase("love/hate")){
		     if(replacementQueue.contains(frameIndex))
            replacementQueue.remove(replacementQueue.indexOf(frameIndex));
            
             if(lovedQueue.contains(frameIndex))
            lovedQueue.remove(lovedQueue.indexOf(frameIndex));
		}
		else {
		    if(replacementQueue.contains(frameIndex))
            replacementQueue.remove(replacementQueue.indexOf(frameIndex));
		}
	}

	public int getCandidateFrame() {
		// FIFO, LRU, MRU, love/hate
		if(!originalQueue.isEmpty())
		{
		    return originalQueue.get(0);
		}
		if (replaceArg.equalsIgnoreCase("FIFO")) {
                for(int i=0;i<replacementQueue.size();i++){
                    if(bufDescrArray[replacementQueue.get(i)].getPinCount()>0)
                    {
                        return replacementQueue.get(i);
                    }
                }
                return -1;
        }
		if (replaceArg.equalsIgnoreCase("MRU")) {
		    if (replacementQueue.isEmpty())
            return -1;
            return replacementQueue.get(replacementQueue.size()-1);
		}
		if (replaceArg.equalsIgnoreCase("LRU")) {
		    if (replacementQueue.isEmpty())
            return -1;
            return replacementQueue.get(0);
		}
		if (replaceArg.equalsIgnoreCase("love/hate")) {

            if (!replacementQueue.isEmpty())
             return replacementQueue.get(0);
             
              if (!lovedQueue.isEmpty())
             return lovedQueue.get(lovedQueue.size()-1);
		}

		
		return -1;
	}

	public void unpinPage(PageId pageno, boolean dirty, boolean loved) throws InvalidPageNumberException, PageUnpinnedException {
		// TODO Auto-generated method stub
		if (idIndexMap.containsKey(pageno)) {
			int bufferId = idIndexMap.get(pageno);
			if (dirty == true) {
				// set the frame dirty boolean in the array to true
				bufDescrArray[bufferId].setDirtyBit(true);
			}
			if (loved == true &&bufDescrArray[bufferId].isLoved()==false) {
				bufDescrArray[bufferId].setLoved(true);
			}
			try {
				bufDescrArray[bufferId].decreasePinCount();
				pageUnpinned(bufferId);
			} catch (PageUnpinnedException e) {
				// e.printStackTrace();
				throw e;
				// System.out.println("PageUnpinnedException thrown.");
			}
		} else {
			throw new InvalidPageNumberException(new Exception(), "BufMgr.java: Page ID Not found By unpin page method");
		}
	}

	private void pageUnpinned(int frameIndex) {
		// TODO Auto-generated method stub
	
		if(bufDescrArray[frameIndex].getPinCount()==0)
		{
		    numBufs++;
            if (replaceArg.equalsIgnoreCase("FIFO")) {
    		    //  if(!replacementQueue.contains(frameIndex))
                //  replacementQueue.add(frameIndex);
    		}else if(replaceArg.equalsIgnoreCase("love/hate"))
    		{
    		    if(bufDescrArray[frameIndex].isLoved()&&!lovedQueue.contains(frameIndex))
    		    {
    		        lovedQueue.add(frameIndex);
    		    }
    		    else if(!bufDescrArray[frameIndex].isLoved()&&!replacementQueue.contains(frameIndex))
    		    {
    		        replacementQueue.add(frameIndex);
    		    }
    		}
    		else
    		{
                if(!replacementQueue.contains(frameIndex))
                {
                    replacementQueue.add(frameIndex);
                }
    		}
		}
	}

	public int getNumUnpinnedBuffers() {
		// TODO Auto-generated method stub
		
		return numBufs;
	}

	public PageId newPage(Page pg, int numPages) throws ChainException, InvalidRunSizeException, InvalidPageNumberException, FileIOException, DiskMgrException, IOException {
		  // TODO Auto-generated method stub
		  int candidateIndex = getCandidateFrame();
		  if(candidateIndex == -1)
		  {
		   return null;
		  }
		  PageId newPageId = new PageId();
		  try
		  {
		   SystemDefs.JavabaseDB.allocate_page(newPageId, numPages);
		  }
		  catch(InvalidRunSizeException | InvalidPageNumberException | FileIOException | DiskMgrException | IOException e)
		  {
		   throw e;
		  }
		  pinPage(newPageId, pg, false);
		  return newPageId;
		 }
		

	public void freePage(PageId pid) throws Exception {
		// TODO Auto-generated method stub
		try {
			SystemDefs.JavabaseDB.deallocate_page(pid);
		} catch (InvalidRunSizeException | InvalidPageNumberException | FileIOException | DiskMgrException | IOException e) {
			// TODO Auto-generated catch block
			throw e;
		}
	}

	public void flushPage(PageId pgid) throws InvalidPageNumberException, FileIOException, IOException {
		Page tempPage = new Page();
		tempPage.setpage(bufpool[idIndexMap.get(pgid)]);
		try {
			SystemDefs.JavabaseDB.write_page(pgid, tempPage);
		} catch (InvalidPageNumberException | FileIOException | IOException e) {
			throw e;// ("BufMgr.java: pinPage() failed", e.getCause());
		}
	}
}