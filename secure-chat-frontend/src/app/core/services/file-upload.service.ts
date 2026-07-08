import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FileUploadResponse } from '../../shared/models/message.dto';

/**
 * REST service for file/image uploads.
 *
 * Backend endpoint: POST /api/upload
 * Controller: FileUploadController
 *
 * Returns a FileUploadResponse with the stored file URL,
 * content type, and original filename. The URL can then be
 * included in a WebSocket message payload as attachmentUrl.
 */
@Injectable({ providedIn: 'root' })
export class FileUploadService {

  private readonly baseUrl = `${environment.apiUrl}/upload`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Uploads a file to the server.
   *
   * @param file the file to upload (from an <input type="file">)
   * @returns Observable with the file URL, content type, and original name
   */
  upload(file: File): Observable<FileUploadResponse> {
    const formData = new FormData();
    formData.append('file', file, file.name);

    return this.http.post<FileUploadResponse>(this.baseUrl, formData);
  }
}
