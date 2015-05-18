/**
 * Filter to format a number as a percentage
 * It assumes that the input is in decimal form, i.e. 15% is 0.15
 */
angular.module('myApp').filter('percentage', ['$filter', function ($filter) {
  return function (input, decimals) {
    return $filter('number')(input * 100, decimals) + '%';
  };
}]);