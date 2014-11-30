package com.smanzana.Project3.Utils;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * A poor implementation of a circular iterator. It's not the point.
 * @author Skyler
 */
public class CircularList<o> {
	
	private List<o> elements;
	
	private int index;
	
	public CircularList() {
		this.elements = new LinkedList<o>();
		index = 0;
	}
	
	public void add(o e) {
		elements.add(e);
	}
	
	public boolean remove(o e) {
		return elements.remove(e);
	}
	
	public void clear() {
		elements.clear();
	}
	
	public void resetIndex() {
		index = 0;
	}
	
	public o next() {
		o obj = elements.get(index);
		index++;
		return obj;
	}
	
	public o prev() {
		index--;
		o obj = elements.get(index);
		return obj;
	}
	
	public void addAll(Collection<? extends o> col) {
		elements.addAll(col);
	}
	
	public boolean isEmpty() {
		return elements.isEmpty();
	}
	
	public boolean contains(o e) {
		return elements.contains(e);
	}
	
	public int size() {
		return elements.size();
	}
	
	public int getIndex() {
		return index;
	}
	
}
