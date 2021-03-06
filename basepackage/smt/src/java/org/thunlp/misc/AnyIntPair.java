package org.thunlp.misc;

public class AnyIntPair<FirstType> {
	public FirstType first;
	public int second;

	public AnyIntPair() {
		first = null;
		second = 0;
	}

	public AnyIntPair(FirstType first, int second) {
		this.first = first;
		this.second = second;
	}

	public String toString() {
		return first + ":" + second;
	}
}
