import os
import subprocess
import sys
import time
import unified_format_parser

start = time.time()

# get arguments
repo  = sys.argv[1]
date1 = sys.argv[2]
date2 = sys.argv[3]

# change to repo
os.chdir(repo)

# get revisions at the dates
revList1 = subprocess.check_output("hg log -d " + date1 + " --template \"{rev}\n\"").decode('utf-8').split("\n")
revList2 = subprocess.check_output("hg log -d " + date2 + " --template \"{rev}\n\"").decode('utf-8').split("\n")
rev1 = min(map(int, revList1[0:len(revList1) - 1]))
rev2 = min(map(int, revList2[0:len(revList2) - 1]))
print(str(rev1))
print(str(rev2))
print(str(rev2 - rev1))

# get result
diff_detail = subprocess.check_output("hg diff -r " + str(rev1) + " -r " + str(rev2)).decode('ISO-8859-1')

# parse result
unified_format_parser.parse_unified_format(diff_detail)

end = time.time()
#print(str(end - start))
