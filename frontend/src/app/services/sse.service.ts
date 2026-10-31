import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class SseService {
  private base = (typeof window !== 'undefined' && (window as any).__QF_API_BASE__) || '/api';

  sseEvents(id: string): EventSource {
    return new EventSource(`${this.base}/runs/${id}/events`);
  }
}
