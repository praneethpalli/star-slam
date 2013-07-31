package starslam.scan

import groovy.sql.Sql

import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

import starslam.IDbConnection

import com.google.inject.Inject
import com.google.inject.name.Named

class ScanService implements IScanService {
	Sql sql
	
	public ScanService(IDbConnection conn) {
		sql = conn.getConnection()
	}
	
	@Inject
	public ScanService(@Named("JDBC_URL") String dbUrl) {
		this({
			Sql.newInstance(dbUrl, '', '', 'org.h2.Driver')
			} as IDbConnection
		)
	}
	
	int countScans() {
		def row = sql.firstRow("select count(*) cnt from scan where project_id")
		return row.cnt
	}
	
	def configRowMapper = { it ->
		if (!it) { return [:] }
		
		return [
			scanId : it.scan_id
			, created : it.created
			, name : it.name
			, md5 : it.md5
			, isNew : it.is_new
			, hasChanged : it.has_changed
		]
	}
	
	def sqlFileRowMapper = { it ->
		if (!it) { return [:] }
		
		return [
			scanId : it.scan_id
			, created : it.created
			, name : it.name
			, md5 : it.md5
			, isNew : it.is_new
		]
	}

	void scan(RunScanArguments args) {
		def project = persistProject(args.projectName)
		def scan = persistScan(args, project)
		scanConfigs(scan)
		scanSql(scan)
	}

	def latestScan(String projectName) {
		def projectRow = sql.firstRow("select * from project where name = ${projectName}")
		def scanRow = sql.firstRow("select * from scan where project_id = ${projectRow.id} order by created desc")
		def rtn = [:]
		rtn.with {
			name = projectRow.name
			created = projectRow.created
			projectRoot = scanRow.root_path
			configFilePattern = scanRow.config_file_pattern
			sqlFileDirectory = scanRow.sql_file_directory
			deployTime = scanRow.deploy_time
			configFiles = []
			sqlFiles = []
		}
		
		sql.eachRow("select * from configfile where scan_id = ${scanRow.id}") {
			rtn.configFiles.add(configRowMapper(it))
		}
		
		sql.eachRow("select * from sqlFile where scan_id = ${scanRow.id}") {
			rtn.sqlFiles.add(sqlFileRowMapper(it))
		}

		rtn
	}
	
	private void scanFilesForMatch(scan, String glob, Closure onMatchFound) {
		def matcher = FileSystems.getDefault().getPathMatcher("glob:"+glob);
		Files.walkFileTree(Paths.get(scan.directory), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException {
				def fileName = filePath.fileName
				if (matcher.matches(fileName)) {
					onMatchFound(filePath)
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private void scanConfigs(scan) {
		scanFilesForMatch(scan, scan.configFilePattern) { filePath ->
			def md5 = generateMd5(new File(filePath.toString()))
			def previousFile = configRowMapper(sql.firstRow("select * from configfile where name = ${filePath.toString()} order by created desc limit 1"))
			def isNew = previousFile.isEmpty()
			def hasChanged = (!isNew && previousFile.md5 != md5)
			
			sql.execute("""
						insert into ConfigFile (
							id
							, scan_id
							, created
							, name	
							, md5
							, is_new
							, has_changed
						)
						values (
							${UUID.randomUUID().toString()}
							, ${scan.id}
							, ${new Date().time}
							, ${filePath.toString()}
							, ${md5}
							, ${isNew}
							, ${hasChanged}
						)
					""")
		}
	}
	
	private void scanSql(scan) {
		scanFilesForMatch(scan, "*.sql") { filePath ->
			def md5 = generateMd5(new File(filePath.toString()))
			def previousFile = sqlFileRowMapper(sql.firstRow("select * from sqlfile where name = ${filePath.toString()} order by created desc limit 1"))
			def isNew = previousFile.isEmpty()
			
			sql.execute("""
				insert into SqlFile (
					id
					, scan_id
					, created
					, name
					, md5
					, is_new
				)
				values (
					${UUID.randomUUID().toString()}
					, ${scan.id}
					, ${new Date().time}
					, ${filePath.toString()}
					, ${md5}
					, ${isNew}
				)
			""")
		}
	}

	private def persistProject(String projectName) {
		def rtn = [:]
		def row = sql.firstRow("select * from project where name = ${projectName}")
		if (row) {
			rtn.with {
				id = row.id
				name = row.name
				created = row.created
			}
		}
		else {
			def newId = UUID.randomUUID().toString()
			def time = new Date().time
			sql.execute("insert into project (id, name, created) values (${newId}, ${projectName}, ${time})")
			rtn.id = newId
			rtn.name = projectName
			rtn.created = time
		}
		return rtn
	}

	private def persistScan(RunScanArguments args, project) {
		def id = UUID.randomUUID().toString()
		def date = new Date().time
		def deployTime = new Date().time
		sql.execute("""
			insert into scan (
				id
				, project_id
				, directory
				, created
				, CONFIG_FILE_PATTERN
				, sql_file_directory
				, deploy_time
			) 
			values (
				${id}
				, ${project.id}
				, ${args.projectRoot}
				, ${date}
				, ${args.configFilePattern}
				, ${args.sqlFileDirectory}
				, ${deployTime}
			)
		""")
		return [
			id : id
			, projectId : project.id
			, created : date
			, directory : args.projectRoot
			, configFilePattern : args.configFilePattern
			, sqlFileDirectory : args.sqlFileDirectory
			, deployTime : deployTime
		]
	}

	private def generateMd5(final file) {
		def digest = MessageDigest.getInstance("MD5")
		file.withInputStream(){is->
			byte[] buffer = new byte[8192]
			int read = 0
			while( (read = is.read(buffer)) > 0) {
				digest.update(buffer, 0, read);
			}
		}
		byte[] md5sum = digest.digest()
		BigInteger bigInt = new BigInteger(1, md5sum)
		return bigInt.toString(16)
	}
}