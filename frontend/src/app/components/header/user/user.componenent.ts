import { Component, inject } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '@app/auth/services/auth.service';
import { LinkComponent } from "@app/components/shared";

@Component({
  selector: 'app-user',
  imports: [MatButtonModule, MatMenuModule, MatIcon, LinkComponent],
  template: `
    <button matIconButton class="border-primary border!" [matMenuTriggerFor]="user">
      <mat-icon fontSet="material-icons-outlined">person</mat-icon>
    </button>
    <mat-menu #user="matMenu">
      <app-link variant="text" route="/profile">Configuracion</app-link>
      <button (click)="onLogout()" title="Cerrar sesión" mat-menu-item>Cerrar Sesion</button>
    </mat-menu>
  `,
  styles: ``,
})
export class UserComponenent {
  private authService = inject(AuthService);

  onLogout(): void {
    this.authService.logout();
    window.location.href = '/login';
  }
}
