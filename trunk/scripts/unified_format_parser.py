def parse_unified_format(diff_detail):
	# details to get
	linesInserted = 0
	linesRemoved  = 0
	filesModified = 0
	filesAdded    = 0
	filesRemoved  = 0

	lineList = diff_detail.split("\n");
	for line in lineList: 
		if line.startswith("--- "):
			if line.startswith("--- /dev/null"):
				filesAdded += 1
			else:
				filesModified += 1
		elif line.startswith("+++ "):
			if line.startswith("+++ /dev/null"):
				filesRemoved += 1
				filesModified -= 1	
		elif line.startswith("+"):
			linesInserted += 1
		elif line.startswith("-"):
			linesRemoved += 1
		elif line.startswith("Binary file ") and line.endswith(" has changed"):
			filesModified += 1

	print(str(linesInserted))
	print(str(linesRemoved))
	print(str(filesModified))	
	print(str(filesAdded))
	print(str(filesRemoved))
