package clientMaster;

public class TestResult {
	
	private int rank;
	private String testType;
	private String[] parameters;
	private String results;
	
	public TestResult(int r, String tt, String[] p, String res) {
		rank = r;
		testType = tt;
		parameters = p;
		results = res;
	}
	
	public int getPosition() {
		return rank;
	}
	
	public String getType() {
		return testType;
	}
	
	public String[] getMetric() {
		return parameters.clone();
	}
	
	public String getResults() {
		return results;
	}
	
	public String toString() {
		String s = "Test type: " + testType + " for ";
		for (String p: parameters) {
			s += p;
			if (!p.equals(parameters[parameters.length-1])) {
				s += ", ";
			}
		}
		s += "\n";
		s += "Test number: " + rank + "\n";
		s += "Results are the following: " + results;
		return s;
	}
	
}
