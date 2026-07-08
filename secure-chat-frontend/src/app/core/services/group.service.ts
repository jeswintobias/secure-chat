import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { GroupResponse, CreateGroupRequest, JoinGroupRequest } from '../../shared/models/group.dto';
import { UserResponse } from '../../shared/models/user.dto';

/**
 * REST service for group conversation management.
 *
 * Backend endpoints: /api/groups/**
 * Controller: GroupController
 */
@Injectable({ providedIn: 'root' })
export class GroupService {

  private readonly baseUrl = `${environment.apiUrl}/groups`;

  constructor(private readonly http: HttpClient) {}

  /** GET /api/groups — Lists all available groups. */
  listGroups(): Observable<GroupResponse[]> {
    return this.http.get<GroupResponse[]>(this.baseUrl);
  }

  /** GET /api/groups/my-conversations — Lists all conversations (GROUP + PRIVATE) for the current user. */
  getMyConversations(): Observable<GroupResponse[]> {
    return this.http.get<GroupResponse[]>(`${this.baseUrl}/my-conversations`);
  }

  /** POST /api/groups — Creates a new group conversation. */
  createGroup(request: CreateGroupRequest): Observable<GroupResponse> {
    return this.http.post<GroupResponse>(this.baseUrl, request);
  }

  /** POST /api/groups/{id}/join — Joins a group via referral code. */
  joinGroup(groupId: string, request: JoinGroupRequest): Observable<GroupResponse> {
    return this.http.post<GroupResponse>(`${this.baseUrl}/${groupId}/join`, request);
  }

  /** GET /api/groups/{id}/members — Lists all group members. */
  getMembers(groupId: string): Observable<UserResponse[]> {
    return this.http.get<UserResponse[]>(`${this.baseUrl}/${groupId}/members`);
  }

  /** DELETE /api/groups/{id}/members/{userId} — Removes a member. */
  removeMember(groupId: string, userId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${groupId}/members/${userId}`);
  }
}
