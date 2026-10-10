import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ConfirmComponent } from './shared/confirm';
import { ToastsComponent } from './shared/toast';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastsComponent, ConfirmComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <router-outlet />
    <app-confirm />
    <app-toasts />
  `,
})
export class App {}
