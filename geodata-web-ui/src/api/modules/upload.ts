import { http } from '@/api/request'

/** 通用分片上传（UploadController，3 端点） */
export const uploadApi = {
  /** 上传单个分片：multipart file + chunk + guid */
  addChunk: (
    file: Blob,
    chunk: number,
    guid: string,
    fileName: string,
    onProgress?: (p: number) => void,
  ) => {
    const fd = new FormData()
    // 后端按 guid + 原始文件名 建分片目录；Blob 默认名 "blob" 会导致 FileMerge 找不到分片。
    fd.append('file', file, fileName)
    fd.append('chunk', String(chunk))
    fd.append('guid', guid)
    return http.postFile<string>('/upload/UploadForBreakpoint/AddFiles', fd, { onProgress })
  },

  /** 已上传分片号（升序，从 1 开始），供断点续传跳过已传分片 */
  parts: (guid: string, fileName: string) =>
    http.get<number[]>('/upload/UploadForBreakpoint/Parts', { guid, fileName }),

  /** 合并分片：multipart fileName + guid → FileUploadDto */
  fileMerge: (fileName: string, guid: string) => {
    const fd = new FormData()
    fd.append('fileName', fileName)
    fd.append('guid', guid)
    return http.postFile<any>('/upload/UploadForBreakpoint/FileMerge', fd)
  },
}

/**
 * SHP 系列上传（KneeCoordinatesController，2 端点）
 * 注意：无类级前缀，路径自带 /Kneecoordinates 且首字母大写。
 */
export const shpApi = {
  /** 多文件上传：multipart file(List) + folderId */
  uploadShpFiles: (files: File[], folderId?: string, onProgress?: (p: number) => void) => {
    const fd = new FormData()
    files.forEach((f) => fd.append('file', f))
    if (folderId) fd.append('folderId', folderId)
    return http.postFile<string>('/Kneecoordinates/UploadShpFiles', fd, { onProgress })
  },

  /** 读取 SHP 生成合并 WKT */
  getShpFileWKT: (folderId: string) => {
    const fd = new FormData()
    fd.append('folderId', folderId)
    return http.postFile<string>('/Kneecoordinates/GetShpFileWKT', fd)
  },
}

export default uploadApi
