(function() {
  'use strict';

  var app = angular.module('myApp');

  angular.module('myApp').factory(
      'ohlcGraph',
      function() {
        var service = {};
        var chartSeries = [];
        var groupingUnits = [ [ 'week', [ 1 ] ], [ 'day', [ 1 ] ],
            [ 'month', [ 1, 2, 3, 6 ] ] ];

        var chartConfig = {
          options : {
            // axis
            xAxis : [ {
              type : 'datetime'
            } ],
            yAxis : [ {
              labels : {
                align : 'right',
                x : -3
              },
              title : {
                text : 'OHLC'
              },
            } ],
            // navigator
            navigator : {
              enabled : true,
              series : {
                data : chartSeries[0]
              }
            },
            // plot options
            plotOptions : {
              candlestick : {
                color : '#5cb85c',
                upColor : '#d9534f'
              }
            },
            // range selector and buttons
            rangeSelector : {
              enabled : true,
              buttonTheme : {
                width : null,
                padding : 2
              },
              buttons : [ {
                type : 'day',
                count : 1,
                text : '1 day'
              }, {
                type : 'day',
                count : 7,
                text : '1 week'
              }, {
                type : 'month',
                count : 1,
                text : '1 month'
              }, {
                type : 'month',
                count : 6,
                text : '6 months'
              }, {
                type : 'year',
                count : 1,
                text : '1 year'
              }, {
                type : 'all',
                text : 'All'
              } ]
            },
          },
          series : chartSeries,
          title : {
            text : 'Market price'
          },
          credits : {
            enabled : false
          },
          loading : true,
          size : {}
        };

        service.getChartConfig = function() {
          return chartConfig;
        };

        service.getChartSeries = function() {
          return chartSeries;
        };

        // service.addData = function(seriesId, data) {
        // var ohlcSeries = chartSeries.filter(function(series) {
        // return series.id == seriesId;
        // })[0];
        // }

        return service;
      });

})();