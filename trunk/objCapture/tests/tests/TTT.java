package tests;

public class TTT {
	void ttt() {
	}

	public void t() {
		InstTestClass itc = new InstTestClass();

		for (int i = 0; i < 5; i++) {
			itc.foox(i);
			for (int j = 0; j < 5; j++) {
				itc.fooy(j);
				System.out.println(itc.toString());
			}
		}

	}

}
