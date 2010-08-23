package tests;

public class InstTestClass {
	int x;
	int y;

	public String toString() {
		return this.getClass().getName() + " x: " + x + " y: " + y ;
	}

	public void foox(int x) {
		this.x = x;
	}
	
	public void fooy(int y){
		this.y = y;
	}

	public InstTestClass(int x, int y) {
		this.x = x;
		this.y = y;
	}
	
	public InstTestClass() {
		this.x = 0;
		this.y = 0;
	}
	
}
