import { Component, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  templateUrl: './empty-state.component.html',
  styleUrl: './empty-state.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyStateComponent {}
