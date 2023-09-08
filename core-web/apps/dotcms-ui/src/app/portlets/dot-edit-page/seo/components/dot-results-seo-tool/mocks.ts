const seoOGTagsMock = {
    description:
        'Get down to Costa Rica this winter for some of the best surfing int he world. Large winter swell is pushing across the Pacific.',
    language: 'english',
    favicon: 'http://localhost:8080/application/themes/landing-page/img/favicon.ico',
    title: 'A title',
    author: 'dotCMS',
    copyright: 'dotCMS LLC, Miami Florida, US',
    'og:title': 'A title',
    'og:url': 'https://dotcms.com$!{dotPageContent.canonicalUrl}',
    'og:image': 'https://dotcms.com/images/default.png'
};

const seoOGTagsResultMock = [
    {
        key: 'Favicon',
        keyIcon: 'pi-check-circle',
        keyColor: 'results-seo-tool__result-icon--alert-green',
        items: [
            {
                message: 'FavIcon found!',
                color: 'results-seo-tool__result-icon--alert-green',
                itemIcon: 'pi-check'
            }
        ],
        sort: 1
    },
    {
        key: 'Description',
        keyIcon: 'pi-exclamation-triangle',
        keyColor: 'results-seo-tool__result-icon--alert-red',
        items: [
            {
                message: 'Meta Description not found! Showing Description instead.',
                color: 'results-seo-tool__result-icon--alert-red',
                itemIcon: 'pi-times'
            }
        ],
        sort: 2,
        info: "The length of the description allowed will depend on the reader's device size; on the smallest size only about 110 characters are allowed."
    },
    {
        key: 'Title',
        keyIcon: 'pi-exclamation-triangle',
        keyColor: 'results-seo-tool__result-icon--alert-yellow',
        items: [
            {
                message: 'HTML Title found, but has fewer than 30 characters of content.',
                color: 'results-seo-tool__result-icon--alert-yellow',
                itemIcon: 'pi-exclamation-circle'
            }
        ],
        sort: 3,
        info: 'HTML Title content should be between 30 and 60 characters.'
    },
    {
        key: 'Og:title',
        keyIcon: 'pi-check-circle',
        keyColor: 'results-seo-tool__result-icon--alert-green',
        items: [
            {
                message: 'og:title metatag found, with an appropriate amount of content!',
                color: 'results-seo-tool__result-icon--alert-green',
                itemIcon: 'pi-exclamation-circle'
            }
        ],
        sort: 4,
        info: 'HTML Title content should be between 30 and 60 characters.'
    },
    {
        key: 'Og:image',
        keyIcon: 'pi-check-circle',
        keyColor: 'results-seo-tool__result-icon--alert-green',
        items: [
            {
                message: 'og:image metatag found, with an appropriate sized image!',
                color: 'results-seo-tool__result-icon--alert-green',
                itemIcon: 'pi-exclamation-circle'
            }
        ],
        sort: 5,
        info: 'HTML Title content should be between 30 and 60 characters.'
    }
];

const seoOGTagsResultOgMock = [
    {
        key: 'description',
        keyIcon: 'pi-exclamation-triangle',
        keyColor: 'results-seo-tool__result-icon--alert-red',
        items: [
            {
                message: 'Meta Description not found! Showing Description instead.',
                color: 'results-seo-tool__result-icon--alert-red',
                itemIcon: 'pi-times'
            }
        ],
        sort: 2,
        info: 'The length of the description allowed will depend on the readers device size; on the smallest size only about 110 characters are allowed.'
    },
    {
        key: 'og:image',
        keyIcon: 'pi-check-circle',
        keyColor: 'results-seo-tool__result-icon--alert-green',
        items: [
            {
                message: 'og:image metatag found, with an appropriate sized image!',
                color: 'results-seo-tool__result-icon--alert-green',
                itemIcon: 'pi-check'
            }
        ],
        sort: 6,
        info: ''
    },
    {
        key: 'og:title',
        keyIcon: 'pi-exclamation-triangle',
        keyColor: 'results-seo-tool__result-icon--alert-yellow',
        items: [
            {
                message: 'seo.rules.og-title.less',
                color: 'results-seo-tool__result-icon--alert-yellow',
                itemIcon: 'pi-exclamation-circle'
            }
        ],
        sort: 5,
        info: 'HTML Title content should be between 30 and 60 characters.'
    },
    {
        key: 'favicon',
        keyIcon: 'pi-check-circle',
        keyColor: 'results-seo-tool__result-icon--alert-green',
        items: [
            {
                message: 'FavIcon found!',
                color: 'results-seo-tool__result-icon--alert-green',
                itemIcon: 'pi-check'
            }
        ],
        sort: 1,
        info: ''
    },
    {
        key: 'title',
        keyIcon: 'pi-exclamation-triangle',
        keyColor: 'results-seo-tool__result-icon--alert-yellow',
        items: [
            {
                message: 'HTML Title found, but has fewer than 30 characters of content.',
                color: 'results-seo-tool__result-icon--alert-yellow',
                itemIcon: 'pi-exclamation-circle'
            }
        ],
        sort: 4,
        info: 'HTML Title content should be between 30 and 60 characters.'
    },
    {
        key: 'og:description',
        keyIcon: 'pi-exclamation-triangle',
        keyColor: 'results-seo-tool__result-icon--alert-red',
        items: [
            {
                message: 'Meta Description not found! Showing Description instead.',
                color: 'results-seo-tool__result-icon--alert-red',
                itemIcon: 'pi-times'
            }
        ],
        sort: 3,
        info: 'The length of the description allowed will depend on the readers device size; on the smallest size only about 110 characters are allowed.'
    }
];

export { seoOGTagsMock, seoOGTagsResultMock, seoOGTagsResultOgMock };
