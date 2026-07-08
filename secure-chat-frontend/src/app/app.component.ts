import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Root application shell — thin wrapper containing only the router outlet.
 * All visual content is rendered by feature components via routing.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: '<router-outlet />',
  styles: [':host { display: block; height: 100vh; width: 100vw; }'],
})
export class AppComponent {}
