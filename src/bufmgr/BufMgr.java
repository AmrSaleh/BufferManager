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
	private HashMap<Integer, Integer> idIndexMap;
	private int numBufs;
	private int initialNumBufs;
	private String replaceArg;
	private ArrayList<Integer> replacementQueue = new ArrayList<Integer>();
	private ArrayList<Integer> lovedQueue = new ArrayList<Integer>();
	private ArrayList<Integer> originalQueue = new ArrayList<Integer>();

	public BufMgr(int numBufs, String replaceArg1) {

		this.numBufs = numBufs;
		this.initialNumBufs = numBufs;
		this.replaceArg =replaceArg1;
		bufpool = new byte[numBufs][MINIBASE_PAGESIZE];
		bufDescrArray = new BufDescr[numBufs];

		for (int i = 0; i < bufDescrArray.length; i++) {
			bufDescrArray[i] = new BufDescr(-999);
		}
		idIndexMap = new HashMap<Integer, Integer>();
//		System.out.println("The idIndexMap " + idIndexMap.toString());
		 if(replaceArg.equalsIgnoreCase("Clock")){
		 replaceArg = "FIFO";
		 }

		if (!replaceArg.equalsIgnoreCase("FIFO") && !replaceArg.equalsIgnoreCase("LRU") && !replaceArg.equalsIgnoreCase("MRU") && !replaceArg.equalsIgnoreCase("love/hate")) {
			replaceArg = "LRU";
		}
		
		
		System.out.println("policy is " + replaceArg1);
		System.out.println("policy is set to be " + replaceArg);
		System.out.println();
		for (int i = 0; i < numBufs; i++) {
			originalQueue.add(i);
		}

	}

	public void pinPage(PageId pageno, Page page /* return */, boolean emptyPage) throws InvalidPageNumberException, FileIOException, IOException, BufferPoolExceededException {
		// TODO Auto-generated method stub
		if (idIndexMap.containsKey(pageno.pid)) {
			int bufIndex = idIndexMap.get(pageno.pid);
			bufDescrArray[bufIndex].increasePinCount();
			pagePinned(bufIndex);
			page.setpage(bufpool[bufIndex]);
			return;
		}

		int newFrameNo = getCandidateFrame();
		// System.out.println("pinpage: here candidate: "+newFrameNo);
		// handling if there is no frames free
		if (newFrameNo == -1) {
			throw new BufferPoolExceededException(new Exception(), "BufMgr.java: All memory buffers are full. Please try again later.");
		}

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
		if (!(bufDescrArray[newFrameNo].getPageID().pid == -999)) {
			if (idIndexMap.containsKey(bufDescrArray[newFrameNo].getPageID().pid))
				idIndexMap.remove(bufDescrArray[newFrameNo].getPageID().pid);
//			System.out.println("page : " + bufDescrArray[newFrameNo].getPageID().pid + " deleted from buffer " + newFrameNo);

		}
		idIndexMap.put(pageno.pid, newFrameNo);
		bufDescrArray[newFrameNo].setPageID(pageno.pid);
		bufDescrArray[newFrameNo].setDirtyBit(false);
		bufDescrArray[newFrameNo].setPinCount(1);
		bufDescrArray[newFrameNo].setLoved(false);
		pagePinned(newFrameNo);

		page.setpage(bufpool[newFrameNo]);

		removeCandidateFromOriginalQueue();
	}

	public void removeCandidateFromQueue(int frameId) {
		if (replacementQueue.contains(frameId))
			replacementQueue.remove(replacementQueue.indexOf(frameId));
	}

	public void removeCandidateFromOriginalQueue() {
		if (!originalQueue.isEmpty())
			originalQueue.remove(0);
	}

	public void pagePinned(int frameIndex) {
		// System.out.println("number of free bufs at pinning : " +numBufs);
//		System.out.println("The idIndexMap " + idIndexMap.toString());
//		System.out.println("page pinned : " + bufDescrArray[frameIndex].getPageID().pid + " and now its pincount is " + bufDescrArray[frameIndex].getPinCount() + "  at frame " + frameIndex);
//		System.out.println();
		if (bufDescrArray[frameIndex].getPinCount() == 1) {

			numBufs--;

		}
		if (replaceArg.equalsIgnoreCase("FIFO")) {
			if (!replacementQueue.contains(frameIndex))
				replacementQueue.add(frameIndex);
		} else if (replaceArg.equalsIgnoreCase("love/hate")) {
			if (replacementQueue.contains(frameIndex))
				replacementQueue.remove(replacementQueue.indexOf(frameIndex));

//			if (lovedQueue.contains(frameIndex))
//				lovedQueue.remove(lovedQueue.indexOf(frameIndex));
			
//			if(bufDescrArray[frameIndex].isLoved()){
//				lovedQueue.add(frameIndex);
//			}else{
				replacementQueue.add(frameIndex);
//			}
		} else {
			if (replacementQueue.contains(frameIndex))
				replacementQueue.remove(replacementQueue.indexOf(frameIndex));
			
				replacementQueue.add(frameIndex);
		}
	}

	public int getCandidateFrame() {
		// FIFO, LRU, MRU, love/hate
		if (!originalQueue.isEmpty()) {
			return originalQueue.get(0);
		}
		if (replaceArg.equalsIgnoreCase("FIFO")) {
			for (int i = 0; i < replacementQueue.size(); i++) {
				if (bufDescrArray[replacementQueue.get(i)].getPinCount() == 0) {
					return replacementQueue.get(i);
				}
			}
			return -1;
		}
		if (replaceArg.equalsIgnoreCase("MRU")) {
//			if (replacementQueue.isEmpty())
//				return -1;
//			return replacementQueue.get(replacementQueue.size() - 1);
			
			for (int i = replacementQueue.size()-1; i >= 0; i--) {
				if (bufDescrArray[replacementQueue.get(i)].getPinCount() == 0) {
					return replacementQueue.get(i);
				}
			}
			return -1;
		}
		if (replaceArg.equalsIgnoreCase("LRU")) {
//			if (replacementQueue.isEmpty())
//				return -1;
//			return replacementQueue.get(0);
			
			for (int i = 0; i < replacementQueue.size(); i++) {
				if (bufDescrArray[replacementQueue.get(i)].getPinCount() == 0) {
					return replacementQueue.get(i);
				}
			}
			return -1;
		}
		if (replaceArg.equalsIgnoreCase("love/hate")) {

//			if (!replacementQueue.isEmpty())
//				return replacementQueue.get(0);
//
//			if (!lovedQueue.isEmpty())
//				return lovedQueue.get(lovedQueue.size() - 1);
			
			
			for (int i = 0; i < replacementQueue.size(); i++) {
				if (bufDescrArray[replacementQueue.get(i)].getPinCount() == 0 && !bufDescrArray[replacementQueue.get(i)].isLoved()) {
					return replacementQueue.get(i);
				}
			}
			
			for (int i = replacementQueue.size()-1; i >= 0; i--) {
				if (bufDescrArray[replacementQueue.get(i)].getPinCount() == 0 && bufDescrArray[replacementQueue.get(i)].isLoved()) {
					return replacementQueue.get(i);
				}
			}
			
			return -1;
			
			
		}

		return -1;
	}

	public void unpinPage(PageId pageno, boolean dirty, boolean loved) throws PageUnpinnedException, HashEntryNotFoundException {
		// TODO Auto-generated method stub
		if (idIndexMap.containsKey(pageno.pid)) {
			int bufferId = idIndexMap.get(pageno.pid);
			if (dirty == true) {
				// set the frame dirty boolean in the array to true
				bufDescrArray[bufferId].setDirtyBit(dirty);
			}
			if (loved == true) {
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
			throw new HashEntryNotFoundException(new Exception(), "BufMgr.java: Page ID Not found By unpin page method");
		}
	}

	public void pageUnpinned(int frameIndex) {
		// TODO Auto-generated method stub
		// System.out.println("number of free bufs at Unpin : " +numBufs);
		
		if (bufDescrArray[frameIndex].getPinCount() == 0) {

//			System.out.println("page totally unpinned : " + bufDescrArray[frameIndex].getPageID().pid + "  at frame " + frameIndex);
//			System.out.println();

			numBufs++;
			if (replaceArg.equalsIgnoreCase("FIFO")) {
				// if(!replacementQueue.contains(frameIndex))
				// replacementQueue.add(frameIndex);
			} else if (replaceArg.equalsIgnoreCase("love/hate")) {
//				if (bufDescrArray[frameIndex].isLoved() && !lovedQueue.contains(frameIndex)) {
//					lovedQueue.add(frameIndex);
//				} else if (!bufDescrArray[frameIndex].isLoved() && !replacementQueue.contains(frameIndex)) {
//					replacementQueue.add(frameIndex);
//				}
				
				if(bufDescrArray[frameIndex].isLoved()){
					
				}
				
			} else {
//				if (!replacementQueue.contains(frameIndex)) {
//					replacementQueue.add(frameIndex);
//				}
			}
		}
	}

	public int getNumUnpinnedBuffers() {
		// TODO Auto-generated method stub

		return numBufs;
	}

	public int getNumBuffers() {
		return initialNumBufs;
	}

	public PageId newPage(Page pg, int numPages) throws ChainException, InvalidRunSizeException, InvalidPageNumberException, FileIOException, DiskMgrException, IOException {
		// TODO Auto-generated method stub
		int candidateIndex = getCandidateFrame();
		if (candidateIndex == -1) {
			return null;
		}

		PageId newPageId = new PageId();

		// System.out.println("new page id : "+newPageId);

		try {
			SystemDefs.JavabaseDB.allocate_page(newPageId, numPages);
		} catch (InvalidRunSizeException | InvalidPageNumberException | FileIOException | DiskMgrException | IOException e) {
			throw e;
		}

		pinPage(newPageId, pg, false);
		return newPageId;
	}

	public void freePage(PageId pid) throws Exception {
		// TODO Auto-generated method stub

		if (idIndexMap.containsKey(pid.pid)) {
			if (bufDescrArray[idIndexMap.get(pid.pid)].getPinCount() > 1) {
				throw new PagePinnedException(new Exception(), "BufMgr.java: freePage() falied, page doubly pinned");
			}

			if (bufDescrArray[idIndexMap.get(pid.pid)].getPinCount() == 1) {
				unpinPage(pid, false, false);
			}

		}
		try {
			SystemDefs.JavabaseDB.deallocate_page(pid);
		} catch (InvalidRunSizeException | InvalidPageNumberException | FileIOException | DiskMgrException | IOException e) {
			// TODO Auto-generated catch block
			// System.out.println(pid.pid);
			// System.out.println("error in free ..... from bufmgr");
			throw e;
		}
	}

	public void flushPage(PageId pgid) throws InvalidPageNumberException, FileIOException, IOException {
		if (pgid == null || pgid.pid == -999)
			return;

		if (!idIndexMap.containsKey(pgid.pid)) {
//			System.out.println("cannot find id in flush " + pgid.pid);
//			throw new InvalidPageNumberException(new Exception(), "BufMgr.java : flushPage() failed, page not found in buffer");
			return;
		}
		Page tempPage = new Page();
		tempPage.setpage(bufpool[idIndexMap.get(pgid.pid)]);
		try {
			SystemDefs.JavabaseDB.write_page(pgid, tempPage);
			bufDescrArray[idIndexMap.get(pgid.pid)].setDirtyBit(false);
		} catch (InvalidPageNumberException | FileIOException | IOException e) {
			throw e;// ("BufMgr.java: pinPage() failed", e.getCause());
		}
	}

	public void flushAllPages() throws InvalidPageNumberException, FileIOException, IOException {
		Page tempPage = new Page();

		for (int i = 0; i < bufpool.length; i++) {
			tempPage.setpage(bufpool[i]);
			PageId pgid = bufDescrArray[i].getPageID();

			if (pgid.pid == -999 || !bufDescrArray[i].isDirtyBit()) {
				continue;
			}

			try {
				SystemDefs.JavabaseDB.write_page(pgid, tempPage);
			} catch (InvalidPageNumberException | FileIOException | IOException e) {
				throw e;// ("BufMgr.java: pinPage() failed", e.getCause());
			}
		}
	}
}
//dbpath = "C:\\Amr\\minibase-db"; 
//logpath = "C:\\Amr\\minibase-log"; 
//"cmd /k del "