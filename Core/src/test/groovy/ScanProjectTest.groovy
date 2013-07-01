import javax.xml.ws.WebEndpoint;

import starslam.RunScanArguments
import starslam.ScanService
import java.nio.file.*


class ScanProjectTest extends TestBase {
	def S = System.getProperty("file.separator")
	ScanService scanService
	
	protected void onPostSetup() {
		scanService = new ScanService(getConnection)
	}
	
	private Path createProjectRoot(String name) {
		return Files.createTempDirectory("SimpleProject")
	}
	
	private void writeFile(String path, String contents) {
		new File(path).withWriter { writer ->
			writer.writeLine(contents)
		}
	}
	
	private void writeFile(String dir, String file, String contents) {
		new File(dir.toString()).mkdirs()
		new File(dir+'/'+file).withWriter { writer ->
			writer.writeLine(contents)
		}
	}
	
	void test_scan_shouldSaveProjectAndScanAttributes() {
		def args = new RunScanArguments() 
		args.with {
			projectName = "Test Project " + UUID.randomUUID().toString()
			projectRoot = createProjectRoot("SimpleProject").toString()
			configFilePattern = "*.config"
			sqlFileDirectory = "SQL.Migration"
		}
		writeFile(args.projectRoot+/${S}web.config/, "<xml><some><content/></some></xml>")
		
		scanService.scan(args)
		
		def latestScan = scanService.latestScan(args.projectName)
		assert latestScan!= null
		assert args.projectName == latestScan.name
		assert latestScan.created != null
		
		assert args.projectRoot == latestScan.projectRoot
		assert args.configFilePattern == latestScan.configFilePattern
		assert args.sqlFileDirectory == latestScan.sqlFileDirectory
		assert latestScan.deployTime != null
		
		assert latestScan.configFiles != null
		assert latestScan.configFiles.size == 1
		
		def configFile = latestScan.configFiles[0]
		assert configFile.name == args.projectRoot+/${S}web.config/
		assert configFile.md5 != null
		assert configFile.isNew
		assert configFile.hasChanged == false
		
	}
	
	void test_scan_withConfigFileChange_shouldIndicateNotNewAndHasChanged() {
		def args = new RunScanArguments()
		args.with {
			projectName = "Test Project " + UUID.randomUUID().toString()
			projectRoot = createProjectRoot("MutatingProject").toString()
			configFilePattern = "*.config"
			sqlFileDirectory = "SQL.Migration"
		}
		
		def pathToConfig = args.projectRoot+/${S}web.config/
		writeFile(pathToConfig, "Initial content")
		
		def assertConfigFile = { isNew, hasChanged ->
			def latestScan = scanService.latestScan(args.projectName)
			assert latestScan.configFiles != null
			assert latestScan.configFiles.size == 1
			
			def configFile = latestScan.configFiles[0]
			assert configFile.name == pathToConfig
			assert configFile.isNew == isNew
			assert configFile.hasChanged == hasChanged
		}
		
		scanService.scan(args)
		assertConfigFile(true, false)
		
		writeFile(pathToConfig, "Updated Content")
		scanService.scan(args)
		assertConfigFile(false, true)
	}
	
	void test_scan_shouldFindsConfigNLevelDeep() {
		def args = new RunScanArguments()
		args.with {
			projectName = "Test Project " + UUID.randomUUID().toString()
			projectRoot = createProjectRoot("MutatingProject").toString()
			configFilePattern = "*.config"
			sqlFileDirectory = "SQL.Migration"
		}

		def pathToConfig = args.projectRoot+"${S}n-level${S}deep${S}"
		def configFileName = 'web.config'
		writeFile(pathToConfig, configFileName, "Initial content")

		scanService.scan(args)
		
		def latestScan = scanService.latestScan(args.projectName)
		assert latestScan != null
		assert latestScan.configFiles != null
		assert latestScan.configFiles.size == 1
		def configFile = latestScan.configFiles[0]
		assert configFile.name.contains(pathToConfig), "Expected Contians <${pathToConfig}>; Actual: <${configFile.name}>"
		assert configFile.name.contains(configFileName), "Expected Contians <${configFileName}>; Actual Config File Name [${configFile.name}]"
	}
	
	void test_scan_shouldFindConfigFilesWithDifferentExentions() {
		def args = new RunScanArguments()
		args.with {
			projectName = "Test Project " + UUID.randomUUID().toString()
			projectRoot = createProjectRoot("MutatingProject").toString()
			configFilePattern = "*{.config,.xml}"
			sqlFileDirectory = "SQL.Migration"
		}

		def pathToConfig = args.projectRoot
		def configFileName1 = 'web.config'
		def configFileName2 = 'app.xml'
		writeFile(pathToConfig, configFileName1, "Initial content")
		writeFile(pathToConfig, configFileName2, "Initial content")
		writeFile(pathToConfig, 'negative.txt', 'Negative text file. Should not be included in scan')

		scanService.scan(args)
		
		def latestScan = scanService.latestScan(args.projectName)
		assert latestScan != null
		assert latestScan.configFiles != null
		assert latestScan.configFiles.size == 2
		println latestScan.configFiles.find { it.name.contains('.config') }
	}
}