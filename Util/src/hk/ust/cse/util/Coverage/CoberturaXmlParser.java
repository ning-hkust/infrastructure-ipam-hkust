package hk.ust.cse.util.Coverage;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

public class CoberturaXmlParser {
  public CoberturaXmlParser(String coberturaXmlFile) throws FileNotFoundException {
    m_coberturaXmlFile = new File(coberturaXmlFile);
    
    // parse the xml file immediately
    if (m_coberturaXmlFile.exists() && m_coberturaXmlFile.isFile()) {
      System.out.println("Parsing cobertura data file...");
      if (parse()) {
        System.out.println("Parsing finished successfully!");
      }
      else {
        System.out.println("Parsing failed!");
      }
    }
    else {
      throw new FileNotFoundException();
    }
  }

  private boolean parse() {
    try {
      // get Document
      m_coberturaXmlDocument = toDocument(m_coberturaXmlFile);
      m_coberturaXmlRoot     = m_coberturaXmlDocument.getRootElement();
      
      // traverse document to create list
      m_allPackages = new ArrayList<Package>();
      m_htPackagesMap = new Hashtable<String, Package>();
      treeWalk(m_coberturaXmlDocument, m_allPackages, m_htPackagesMap);

      return true;
    } catch (DocumentException e) {
      System.err.println("Xml file is corrupted!");
    }
    return false;
  }

  private Document toDocument(File xmlFile) throws DocumentException {
    SAXReader reader = new SAXReader();
    Document document = reader.read(m_coberturaXmlFile);
    return document;
  }

  private void treeWalk(Document document, List<Package> pkgList,
      Hashtable<String, Package> pkgsMap) {
    treeWalk(document.getRootElement(), pkgList, pkgsMap, null, null, null, null);
  }

  private void treeWalk(Element element, List<Package> pkgList,
      Hashtable<String, Package> pkgsMap, Package curPkg, Class curClass,
      Method curMethod, Line curLine) {

    for (int i = 0, size = element.nodeCount(); i < size; i++) {
      Node node = element.node(i);
      if (node instanceof Element) {
        Element elmn = (Element) node;
        if (elmn.getName().equals("package")) {
          String name       = elmn.attributeValue("name");
          double lineCov    = Double.parseDouble(elmn.attributeValue("line-rate"));
          double branchCov  = Double.parseDouble(elmn.attributeValue("branch-rate"));
          double complexity = Double.parseDouble(elmn.attributeValue("complexity"));
          Package pkg       = new Package(name, lineCov, branchCov, complexity);
          pkgList.add(pkg);
          pkgsMap.put(name, pkg);

          // walk in
          treeWalk(elmn, pkgList, pkgsMap, pkg, null, null, null);
        }
        else if (elmn.getName().equals("class")) {
          String name       = elmn.attributeValue("name");
          String srcFile    = elmn.attributeValue("filename");
          double lineCov    = Double.parseDouble(elmn.attributeValue("line-rate"));
          double branchCov  = Double.parseDouble(elmn.attributeValue("branch-rate"));
          double complexity = Double.parseDouble(elmn.attributeValue("complexity"));
          Class cls         = new Class(name, srcFile, lineCov, branchCov, complexity, curPkg);
          curPkg.addInClass(cls);

          // walk in
          treeWalk(elmn, pkgList, pkgsMap, curPkg, cls, null, null);
        }
        else if (elmn.getName().equals("method")) {
          String name       = elmn.attributeValue("name");
          String signature  = elmn.attributeValue("signature");
          double lineCov    = Double.parseDouble(elmn.attributeValue("line-rate"));
          double branchCov  = Double.parseDouble(elmn.attributeValue("branch-rate"));
          Method method     = new Method(name, signature, lineCov, branchCov, curClass);
          curClass.addInMethod(method);

          // walk in
          treeWalk(elmn, pkgList, pkgsMap, curPkg, curClass, method, null);
        }
        else if (elmn.getName().equals("line")) {
          // neglect listing of all lines
          if (curMethod == null) {
            continue;
          }

          int number     = Integer.parseInt(elmn.attributeValue("number"));
          long hits      = Long.parseLong(elmn.attributeValue("hits"));
          boolean branch = Boolean.parseBoolean(elmn.attributeValue("branch"));
          String condCov = elmn.attributeValue("condition-coverage");
          Line line      = new Line(number, hits, branch, condCov, curMethod);
          curMethod.addInLine(line);

          // walk in
          treeWalk(elmn, pkgList, pkgsMap, curPkg, curClass, curMethod, line);
        }
        else if (elmn.getName().equals("condition")) {
          int number      = Integer.parseInt(elmn.attributeValue("number"));
          String type     = elmn.attributeValue("type");
          String coverage = elmn.attributeValue("coverage");
          Condition cond  = new Condition(number, type, coverage, curLine);
          curLine.addInCondition(cond);
        
          // reached leaf, no need to walk in
        }
        else {
          // continue tree walk
          treeWalk(elmn, pkgList, pkgsMap, curPkg, curClass, curMethod, curLine);
        }
      }
    }
  }

  public double getOverallLineCoverage() {
    if (m_coberturaXmlRoot == null) {
      return -1.0;
    }
    return Double.parseDouble(m_coberturaXmlRoot.attributeValue("line-rate"));
  }

  public double getOverallBranchCoverage() {
    if (m_coberturaXmlRoot == null) {
      return -1.0;
    }
    return Double.parseDouble(m_coberturaXmlRoot.attributeValue("branch-rate"));
  }
  
  public int getOverallLinesCovered() {
    if (m_coberturaXmlRoot == null) {
      return -1;
    }
    return Integer.parseInt(m_coberturaXmlRoot.attributeValue("lines-covered"));
  }
  
  public int getOverallBranchesCovered() {
    if (m_coberturaXmlRoot == null) {
      return -1;
    }
    return Integer.parseInt(m_coberturaXmlRoot.attributeValue("branches-covered"));
  }
  
  public int getOverallLines() {
    if (m_coberturaXmlRoot == null) {
      return -1;
    }
    return Integer.parseInt(m_coberturaXmlRoot.attributeValue("lines-valid"));
  }
  
  public int getOverallBranches() {
    if (m_coberturaXmlRoot == null) {
      return -1;
    }
    return Integer.parseInt(m_coberturaXmlRoot.attributeValue("branches-valid"));
  }
  
  public double getOverallComplexity() {
    if (m_coberturaXmlRoot == null) {
      return -1.0;
    }
    return Double.parseDouble(m_coberturaXmlRoot.attributeValue("complexity"));
  }
  
  public Package getPackage(String pkgName) {
    if (m_coberturaXmlDocument == null) {
      return null;
    }
    
    return m_htPackagesMap.get(pkgName);
  }

  public List<String> getPackageNames() {
    if (m_allPackages == null) {
      return null;
    }

    // get package names
    List<String> pkgNames = new ArrayList<String>();
    for (int i = 0; i < m_allPackages.size(); i++) {
      pkgNames.add(m_allPackages.get(i).getName());
    }
    return pkgNames;
  }
  
  public List<String> getClassNames(String pkgName) {
    if (m_allPackages == null) {
      return null;
    }

    Package pkg = m_htPackagesMap.get(pkgName);
    if (pkg != null) {
      // get class names
      List<String> classNames = new ArrayList<String>();
      for (int i = 0; i < pkg.getAllClasses().size(); i++) {
        classNames.add(pkg.getAllClasses().get(i).getName());
      }
      return classNames;
    }
    return null;
  }

  public List<Package> getAllPackages() {
    return m_allPackages;
  }
  
  public List<Line> getAllBranchLines() {
    List<Line> branchLines = new ArrayList<Line>();
    for (int i = 0, size1 = m_allPackages.size(); i < size1; i++) {
      Package pkg = m_allPackages.get(i);
      for (int j = 0, size2 = pkg.getAllClasses().size(); j < size2; j++) {
        Class cls = pkg.getAllClasses().get(j);
        for (int k = 0, size3 = cls.getAllMethods().size(); k < size3; k++) {
          Method meth = cls.getAllMethods().get(k);
          for (int l = 0, size4 = meth.getAllLines().size(); l < size4; l++) {
            Line line = meth.getAllLines().get(l);
            if (line.isBranch()) {
              branchLines.add(line);
            }
          }
        }
      }
    }
    return branchLines;
  }
  
  /**
   * Get branch lines with uncovered conditions
   */
  public List<Line> getUncoveredBranchLines() {
    List<Line> branchLines = new ArrayList<Line>();
    for (int i = 0, size1 = m_allPackages.size(); i < size1; i++) {
      Package pkg = m_allPackages.get(i);
      for (int j = 0, size2 = pkg.getAllClasses().size(); j < size2; j++) {
        Class cls = pkg.getAllClasses().get(j);
        for (int k = 0, size3 = cls.getAllMethods().size(); k < size3; k++) {
          Method meth = cls.getAllMethods().get(k);
          for (int l = 0, size4 = meth.getAllLines().size(); l < size4; l++) {
            Line line = meth.getAllLines().get(l);
            if (line.isBranch() && (line.getConditionCount() - line.getConditionCovered() > 0)) {
              branchLines.add(line);
            }
          }
        }
      }
    }
    return branchLines;
  }
  
  /**
   * Get branches and branch lines with uncovered conditions
   */
  public List<List<Line>> getUncoveredBranches() {
    List<Line> uncovBranches    = new ArrayList<Line>();
    List<Line> uncovBranchLines = new ArrayList<Line>();
    
    for (int i = 0, size1 = m_allPackages.size(); i < size1; i++) {
      Package pkg = m_allPackages.get(i);
      for (int j = 0, size2 = pkg.getAllClasses().size(); j < size2; j++) {
        Class cls = pkg.getAllClasses().get(j);
        for (int k = 0, size3 = cls.getAllMethods().size(); k < size3; k++) {
          Method meth = cls.getAllMethods().get(k);
          
          Line branchLine = null;
          for (int l = 0, size4 = meth.getAllLines().size(); l < size4; l++) {
            Line line = meth.getAllLines().get(l);
            // add if it is a uncovered branch
            if (branchLine != null && line.getHitCount() == 0) {
              uncovBranches.add(line);
              uncovBranchLines.add(branchLine);
              branchLine = null;  // only add the first uncovered branch
            }
            // check if it is a unfully covered branch line
            if (line.isBranch() && (line.getConditionCount() - line.getConditionCovered() > 0)) {
              branchLine = line;
            }
          }
        }
      }
    }
    
    // create output
    List<List<Line>> ret = new ArrayList<List<Line>>();
    ret.add(uncovBranches);
    ret.add(uncovBranchLines);
    return ret;
  }

  public static void main(String[] args) {
    try {
      CoberturaXmlParser coberturaParser = new CoberturaXmlParser(
          "../experiment/OCAT/apache-commons-math/report_ocat/cobertura/xml/coverage.xml");

      Method method = coberturaParser.getAllPackages().get(37).getAllClasses().get(1).getAllMethods().get(7);
      System.out.println(method.getName() + ": " + method.getAllLines().size());

      Package pkg = coberturaParser.getPackage("org.apache.commons.math.transform");
      System.out.println(pkg.getName());
      
      System.out.println(coberturaParser.getOverallLineCoverage());
      System.out.println(coberturaParser.getOverallBranchCoverage());
      System.out.println(coberturaParser.getOverallLinesCovered());
      System.out.println(coberturaParser.getOverallBranchesCovered());
      System.out.println(coberturaParser.getOverallLines());
      System.out.println(coberturaParser.getOverallBranches());
      System.out.println(coberturaParser.getOverallComplexity());

      System.out.println("packages: ");
      List<String> pkgNames = coberturaParser.getPackageNames();
      for (int i = 0; i < pkgNames.size(); i++) {
        System.out.println(pkgNames.get(i));
      }
      System.out.println(pkgNames.size());
      
      System.out.println("classes: ");
      List<String> classNames = coberturaParser.getClassNames("org.apache.commons.math.analysis.integration");
      for (int i = 0; i < classNames.size(); i++) {
        System.out.println(classNames.get(i));
      }
      System.out.println(classNames.size());

      // get uncovered branches
      List<Line> uncovers = coberturaParser.getUncoveredBranchLines();
      for (int i = 0; i < uncovers.size(); i++) {
        System.out.println(uncovers.get(i).getConditionCoverage());
      }
      System.out.println(uncovers.size());
    } catch (FileNotFoundException e) {
      System.out.println("File not found!");
    }
  }

  private File     m_coberturaXmlFile;
  private Document m_coberturaXmlDocument;
  private Element  m_coberturaXmlRoot;
  private List<Package> m_allPackages;
  private Hashtable<String, Package> m_htPackagesMap;
}
