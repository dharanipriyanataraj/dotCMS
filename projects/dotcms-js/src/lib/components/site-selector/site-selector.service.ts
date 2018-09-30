
import {throwError as observableThrowError,  Observable } from 'rxjs';
import { Response } from '@angular/http';
import { Inject, Injectable } from '@angular/core';
import { HttpClient } from '../../core/util/http.service';
import { NotificationService } from '../../core/util/notification.service';
import { Site } from '../../core/treeable/shared/site.model';
import { ErrorObservable } from 'rxjs/observable/ErrorObservable';
import { map, catchError } from 'rxjs/operators';

@Injectable()
@Inject('dotHttpClient')
@Inject('notificationService')
export class SiteSelectorService {
    constructor(
        private dotHttpClient: HttpClient,
        private notificationService: NotificationService
    ) {}

    /**
     * Returns a list of sites searcing the hostname
     * @param searchQuery
     * @returns Observable<R|T>
     */
    filterForSites(searchQuery: string): Observable<Site[]> {
        return <Observable<Site[]>>(
            this.dotHttpClient.get('/api/v1/site?filter=' + searchQuery + '&archived=false').pipe(
                map((res: Response) => this.extractDataFilter(res)),
                catchError((err) => this.handleError(err))
            )
        );
    }

    /**
     * Returns all sites
     * @returns Observable<R|T>
     */
    getSites(): Observable<Site[]> {
        return <Observable<Site[]>>this.dotHttpClient.get('/api/v1/site/').pipe(
            map((res: Response) => this.extractDataDropdown(res)),
            catchError((err) => this.handleError(err))
        );
    }

    private extractDataDropdown(res: Response): Site[] {
        const obj = JSON.parse(res.text());
        if (obj.entity.sites && obj.entity.sites.results && obj.entity.sites.results.length > 0) {
            return obj.entity.sites.results;
        }
        return obj.entity;
    }

    private extractDataFilter(res: Response): Site[] {
        const obj = JSON.parse(res.text());
        return obj.entity;
    }

    private handleError(error: any): ErrorObservable<string> {
        // we need use a remote logging infrastructure at some point
        const errMsg = error.message
            ? error.message
            : error.status
                ? `${error.status} - ${error.statusText}`
                : 'Server error';
        if (errMsg) {
            console.log(errMsg);
            this.notificationService.displayErrorMessage(
                'There was an error; please try again : ' + errMsg
            );
            return observableThrowError(errMsg);
        }
    }
}
