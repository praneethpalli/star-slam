package starslam.scan;


public interface IScanStore {
	String persist(ScanInfo scan);
	ScanInfo retrieveScan(String id);
	ScanInfo retrieveLatestScanForProject(String projectId);
	Iterable<ScanInfo> scansForProject(String projectId);
	
	String persist(ScannedFile file);
	ScannedFile retrieveLatestScannedFileWithRelativePath(String projectId, String path);
	Iterable<ScannedFile> filesForScan(String scanId);
}