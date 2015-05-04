package bufmgr;

import global.PageId;

public class BufDescr {
//	private PageId pageID;
	private int pinCount;
	private boolean dirtyBit;
	private boolean loved;

	private int pageID;
	public BufDescr(int ID) {
		this.setPageID(ID);
		this.pinCount = 0;
		this.setDirtyBit(false);
		this.setLoved(false);
	}

	public void increasePinCount() {
		this.pinCount++;
	}

	public void decreasePinCount() throws PageUnpinnedException {
		if (this.pinCount == 0) {
			throw new PageUnpinnedException(new Exception(), "BufDescr.java: unpinPage() failed");
		} else {
			this.pinCount--;
		}
	}

	public void setPinCount(int pinCount) {
		this.pinCount = pinCount;
	}

	public int getPinCount() {
		// TODO Auto-generated method stub
		return pinCount;
	}

	/**
	 * @return the loved
	 */
	public boolean isLoved() {
		return loved;
	}

	/**
	 * @param loved
	 *            the loved to set
	 */
	public void setLoved(boolean loved) {
		this.loved = loved;
	}

	/**
	 * @return the pageID
	 */
	public PageId getPageID() {
		PageId id = new PageId();
		id.pid=pageID;
		return id;
	}

	/**
	 * @param pageID
	 *            the pageID to set
	 */
	public void setPageID(int ID) {
		this.pageID = ID;
	}

	/**
	 * @return the dirtyBit
	 */
	public boolean isDirtyBit() {
		return dirtyBit;
	}

	/**
	 * @param dirtyBit
	 *            the dirtyBit to set
	 */
	public void setDirtyBit(boolean dirtyBit) {
		this.dirtyBit = dirtyBit;
	}
}