package com.heima.article.controller.v1.MyExercises;

import java.util.Arrays;
import java.util.Scanner;

//10.10携程第三题

public class NowCoderTest {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int n = scanner.nextInt();
        int m = scanner.nextInt();
        int[] arr = new int[n+1];
        for (int i = 1; i <= n; i++) {
            arr[i] = scanner.nextInt();
        }

        int[][] dp = new int[n+1][m+1];  //dp[i][j]将前i本书分到j个书架时获得的最大GCD和
        for (int[] row : dp) {
            Arrays.fill(row, Integer.MIN_VALUE);
        }
        for(int i = 1; i <= n; i++){
            dp[i][1] = queryGCD(1, i, arr);
        }

        for (int i = 1; i <= n; i++) {
            for (int j = 2; j <= Math.min(i, m); j++) {
                for (int k = j-1; k < i; k++) {  //前一个分割点
                    dp[i][j] = Math.max(dp[i][j], dp[k][j-1] + queryGCD(k+1, i, arr));
                }
            }
        }
        System.out.println(dp[n][m]);
    }

    public static int queryGCD(int l, int r, int[] arr) {
        int ans = arr[l];
        for (int i = l+1; i <= r; i++) {
            ans = gcd(ans, arr[i]);
        }
        return ans;
    }

    //辗转相除法
    public static int gcd(int a, int b){
        while(b != 0){
            int temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }
}
